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
public class CtpTraderGateway implements ITraderGateway, Runnable {

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
    public void insert(Request request) {
        int i;
        try {
            switch (request.getAction()) {
                case ActionType.NEW:
                    i = spi.insertOrder(request);
                    if (i != 0) {
                        spi.setStatus(i, "Sending request failed.");
                        spi.getHandler()
                           .onError(new GatewayRuntimeException(i, "Sending request failed."));
                    }
                    break;
                case ActionType.DELETE:
                    i = spi.deleteOrder(request);
                    if (i != 0) {
                        spi.setStatus(i, "Sending request failed.");
                        spi.getHandler()
                           .onError(new GatewayRuntimeException(i, "Sending request failed."));
                    }
                    break;
                default:
                    spi.getHandler()
                       .onError(new GatewayRuntimeException(
                               -1, "Unknown request action type(" + request.getAction() + ")."));
            }
        } catch (GatewayRuntimeException ex) {
            spi.getHandler().onError(ex);
        } catch (Throwable th) {
            spi.getHandler().onError(new GatewayRuntimeException(-1, th.getMessage(), th));
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

    public void start() {
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

    public void setUserId(String userId) {
        spi.setUserId(userId);
    }

    public void setBrokerId(String brokerId) {
        spi.setBrokerId(brokerId);
    }

    public void setPassword(String password) {
        spi.setPassword(password);
    }

    public void setAppId(String appId) {
        spi.setAppId(appId);
    }

    public void setAuthCode(String authCode) {
        spi.setAuthCode(authCode);
    }

    public void setFlowPath(String flowPath) {
        spi.setFlowPath(flowPath);
    }

    public void addFront(String addr) {
        spi.addFront(addr);
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
                spi.getHandler().onError(new GatewayRuntimeException(null, ex.getMessage(), ex));
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
