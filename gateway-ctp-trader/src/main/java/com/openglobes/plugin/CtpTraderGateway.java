/*
 * Copyright (C) 2021 Hongbao Chen <chenhongbao@outlook.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.openglobes.plugin;

import com.openglobes.core.GatewayRuntimeException;
import com.openglobes.core.trader.*;
import org.ctp4j.CThostFtdcTraderApi;
import org.ctp4j.THOST_TE_RESUME_TYPE;

import java.util.Properties;

/**
 * @author Hongbao Chen
 * @since 1.0
 */
public class CtpTraderGateway implements ITraderGateway,
                                         Runnable {

    private final Thread connThd;
    private final CtpTraderSpi spi;
    private CThostFtdcTraderApi api;

    public CtpTraderGateway() {
        spi = new CtpTraderSpi(this);
        connThd = new Thread(this);
    }

    @Override
    public TraderGatewayInfo getGatewayInfo() {
        return spi.getInfo();
    }

    @Override
    public int getStatus() {
        return spi.getStatus();
    }

    @Override
    public void insert(Request request) {
        int i;
        switch (request.getAction()) {
            case ActionType.NEW:
                i = spi.insertOrder(request);
                if (i != 0) {
                    spi.setStatus(i,
                                  "Sending request failed.");
                    throw new GatewayRuntimeException(i, "Sending request failed.");
                }
                break;
            case ActionType.DELETE:
                i = spi.deleteOrder(request);
                if (i != 0) {
                    spi.setStatus(i, "Sending request failed.");
                    throw new GatewayRuntimeException(i, "Sending request failed.");
                }
                break;
            default:
                throw new GatewayRuntimeException(-1, "Unknown request action type("
                                                      + request.getAction() + ").");
        }
    }

    @Override
    public void run() {
        while (GatewayStatus.NEVER_CONNECTED == spi.getStatus()
               && !Thread.currentThread().isInterrupted()) {
            // TODO Can Init() be called repeatedly?
            api.Init();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Start this gateway with the specifed properties and handler.
     * <p>
     * This method only initiates a connection to the remote server and it
     * doesn't guarantee the successful response from the remote. To make sure
     * its connection succeeds, to inspect it current status, call
     * {@link ITraderGateway#getStatus()} or override
     * {@link ITraderGatewayHandler#onStatusChange}.
     * <p>
     * <p>
     * The following values must be set in properties:
     * <ul>
     * <li><b>AppId</b>: application ID for your client.
     * <li><b>AuthCode</b>: authcentication code for your client.
     * <li><b>BrokerId</b>: broker ID for your account.
     * <li><b>UserId</b>: user ID for your account.
     * <li><b>Password</b>: password for your account.
     * <li><b>FlowPath</b>: flow path on your disk to keep sessional
     * information.
     * <li><b>Front.front-id</b>: front address to connect. The addresses must
     * be formatted like tcp://127.0.0.1:9090 .If you have multiple fronts to
     * connect, replace {@code 'front-id'} with different values so it won't
     * override one another in mapping.
     * </ul>
     * <p>
     * To supply proeprties to the method, you should code like this:
     * <pre>{@code
     *      var properties = new Properties();
     *      // Set authentication information.
     *      properties.put("AppId",
     *                     "my app id");
     *      properties.put("AuthCode",
     *                     "my auth code");
     *      // Set account login information.
     *      properties.put("BrokerId",
     *                     "my broker id");
     *      properties.put("UserId",
     *                     "my user id");
     *      properties.put("Password",
     *                     "my password");
     *      // Set flow cache path.
     *      properties.put("FlowPath",
     *                     "my flow path");
     *      // Set connected front addresses.
     *      properties.put("Front.1",
     *                     "tcp://127.0.0.1:9090");
     *      properties.put("Front.2",
     *                     "tcp://127.0.0.1:9090");
     *      api.start(properties,
     *                myGatewayHandler);
     * }</pre>
     *
     * @param properties properties for the gateway to run with.
     */
    public void start(Properties properties)  {
        spi.setProperties(properties);
        init();
    }

    public void stop() {
        int r = spi.apiLogout();
        if (r != 0) {
            spi.setStatus(r, "Sending logout request failed.");
        }
        api.Release();
        terminateThread();
    }

    private void init() {
        api = CThostFtdcTraderApi.CreateFtdcTraderApi(spi.getFlowPath());
        api.RegisterSpi(spi);
        spi.getFronts().forEach(f -> {
            api.RegisterFront(f);
        });
        api.SubscribePrivateTopic(THOST_TE_RESUME_TYPE.THOST_TERT_RESUME);
        api.SubscribePublicTopic(THOST_TE_RESUME_TYPE.THOST_TERT_RESUME);
        connThd.start();
    }

    private void terminateThread() {
        if (connThd.isAlive()) {
            connThd.interrupt();
            try {
                connThd.join(1000);
            } catch (InterruptedException ex) {
                throw new GatewayRuntimeException(null, ex.getMessage(), ex);
            }
        }
    }

    CThostFtdcTraderApi getApi() {
        return api;
    }

    ITraderGatewayHandler getHandler() {
        return spi.getHandler();
    }

    /**
     * @param handler gateway handler for remote responses.
     */
    @Override
    public void setHandler(ITraderGatewayHandler handler) {
        spi.setHandler(handler);
    }
}
