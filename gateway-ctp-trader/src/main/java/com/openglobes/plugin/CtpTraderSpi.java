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

import org.ctp4j.*;

/**
 * @author Hongbao Chen
 * @since 1.0
 */
class CtpTraderSpi extends AbstractCtpTraderSpi {

    CtpTraderSpi(CtpTraderGateway gateway) {
        super(gateway);
    }

    @Override
    public void OnErrRtnOrderAction(CThostFtdcOrderActionField rsp,
                                    CThostFtdcRspInfoField info) {
        doError(rsp,
                info);
    }

    @Override
    public void OnErrRtnOrderInsert(CThostFtdcInputOrderField rsp,
                                    CThostFtdcRspInfoField info) {
        doError(rsp,
                info);
    }

    @Override
    public void OnFrontConnected() {
        setStatus(GatewayStatus.CONNECTED,
                  "Connected.");
        apiAuthenticate();
    }

    @Override
    public void OnFrontDisconnected(int nReason) {
        setStatus(GatewayStatus.DISCONNECTED,
                  "Disconnected(" + nReason + ").");
    }

    @Override
    public void OnRspAuthenticate(CThostFtdcRspAuthenticateField rsp,
                                  CThostFtdcRspInfoField info,
                                  int requestId,
                                  boolean isLast) {
        if (info == null) {
            return;
        }
        if (info.getErrorID() != 0) {
            setStatus(GatewayStatus.AUTHENTICATE_FAIL,
                      info.getErrorMsg());
            doError(info);
        } else {
            setStatus(GatewayStatus.AUTHENTICATED,
                      info.getErrorMsg());
            apiLogin();
        }
    }

    @Override
    public void OnRspError(CThostFtdcRspInfoField info,
                           int requestId,
                           boolean isLast) {
    }

    @Override
    public void OnRspOrderAction(CThostFtdcInputOrderActionField rsp,
                                 CThostFtdcRspInfoField info,
                                 int requestId,
                                 boolean isLast) {
        doError(rsp,
                info,
                requestId);
    }

    @Override
    public void OnRspOrderInsert(CThostFtdcInputOrderField rsp,
                                 CThostFtdcRspInfoField info,
                                 int requestId,
                                 boolean isLast) {
        doError(rsp,
                info);
    }

    @Override
    public void OnRspSettlementInfoConfirm(CThostFtdcSettlementInfoConfirmField rsp,
                                           CThostFtdcRspInfoField info,
                                           int requestId,
                                           boolean isLast) {
        if (info == null) {
            return;
        }
        if (info.getErrorID() != 0) {
            setStatus(GatewayStatus.CONFIRM_FAIL,
                      info.getErrorMsg());
            doError(info);
        } else {
            setStatus(GatewayStatus.CONFIRMED,
                      info.getErrorMsg());
        }
    }

    @Override
    public void OnRspUserLogin(CThostFtdcRspUserLoginField rsp,
                               CThostFtdcRspInfoField info,
                               int requestId,
                               boolean isLast) {
        if (info == null) {
            return;
        }
        if (info.getErrorID() != 0) {
            setStatus(GatewayStatus.LOGIN_FAIL,
                      info.getErrorMsg());
            doError(info);
        } else {
            setStatus(GatewayStatus.LOGIN,
                      info.getErrorMsg());
            setInfo(rsp);
            apiConfirmSettlement();
        }
    }

    @Override
    public void OnRspUserLogout(CThostFtdcUserLogoutField rsp,
                                CThostFtdcRspInfoField info,
                                int requestId,
                                boolean isLast) {
        if (info == null) {
            return;
        }
        if (info.getErrorID() != 0) {
            setStatus(GatewayStatus.LOGOUT_FAIL,
                      info.getErrorMsg());
            doError(info);
        } else {
            setStatus(GatewayStatus.LOGOUT,
                      info.getErrorMsg());
        }
    }

    @Override
    public void OnRtnOrder(CThostFtdcOrderField order) {
        doOrder(order);
    }

    @Override
    public void OnRtnTrade(CThostFtdcTradeField trade) {
        doTrade(trade);
    }

}
