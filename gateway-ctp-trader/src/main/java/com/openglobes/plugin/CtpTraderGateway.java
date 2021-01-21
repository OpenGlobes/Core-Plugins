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

import com.openglobes.core.GatewayException;
import com.openglobes.core.trader.ActionType;
import com.openglobes.core.trader.ITraderGateway;
import com.openglobes.core.trader.ITraderGatewayHandler;
import com.openglobes.core.trader.Request;
import com.openglobes.core.trader.TraderGatewayInfo;
import java.util.Properties;
import org.ctp4j.CThostFtdcTraderApi;
import org.ctp4j.THOST_TE_RESUME_TYPE;

/**
 *
 * @author Hongbao Chen
 * @since 1.0
 */
public class CtpTraderGateway implements ITraderGateway, Runnable {

    private CThostFtdcTraderApi api;
    private final Thread connectThread;
    private final CtpTraderSpi spi;

    public CtpTraderGateway() {
        spi = new CtpTraderSpi(this);
        connectThread = new Thread(this);
    }

    @Override
    public TraderGatewayInfo getGatewayInfo() {
        return spi.getInfo();
    }

    @Override
    public Properties getProperties() {
        return spi.getProperties();
    }

    @Override
    public int getStatus() {
        return spi.getStatus();
    }

    @Override
    public void insert(Request request,
                       long requestId) throws GatewayException {
        int i;
        switch (request.getAction()) {
            case ActionType.NEW:
                i = spi.insertOrder(request,
                                    requestId);
                if (i != 0) {
                    spi.setStatus(i);
                    throw new GatewayException(i,
                                               "Order insertion failed.");
                }
                break;
            case ActionType.DELETE:
                i = spi.deleteOrder(request,
                                    requestId);
                if (i != 0) {
                    spi.setStatus(i);
                    throw new GatewayException(i,
                                               "Order deletion failed.");
                }
                break;
            default:
                throw new GatewayException(-1,
                                           "Unknown request action type(" + request.getAction() + ").");
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
            }
            catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    public void start(Properties properties,
                      ITraderGatewayHandler handler) throws GatewayException {
        spi.setProperties(properties);
        spi.setHandler(handler);
        init();
    }

    @Override
    public void stop() throws GatewayException {
        int r = spi.logout();
        if (r != 0) {
            spi.setStatus(r);
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
        connectThread.start();
    }

    private void terminateThread() throws GatewayException {
        if (connectThread.isAlive()) {
            connectThread.interrupt();
            try {
                connectThread.join(1000);
            }
            catch (InterruptedException ex) {
                throw new GatewayException(null,
                                           ex.getMessage(),
                                           ex);
            }
        }
    }

    CThostFtdcTraderApi getApi() {
        return api;
    }

    ITraderGatewayHandler getHander() {
        return spi.getHandler();
    }
}
