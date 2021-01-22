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
import com.openglobes.core.ServiceRuntimeStatus;
import com.openglobes.core.trader.ITraderGatewayHandler;
import com.openglobes.core.trader.Request;
import com.openglobes.core.trader.TraderGatewayInfo;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.ctp4j.CThostFtdcInputOrderActionField;
import org.ctp4j.CThostFtdcInputOrderField;
import org.ctp4j.CThostFtdcReqAuthenticateField;
import org.ctp4j.CThostFtdcReqUserLoginField;
import org.ctp4j.CThostFtdcSettlementInfoConfirmField;
import org.ctp4j.CThostFtdcTraderSpi;
import org.ctp4j.CThostFtdcUserLogoutField;

/**
 *
 * @author Hongbao Chen
 * @since 1.0
 */
public class AbstractCtpTraderSpi extends CThostFtdcTraderSpi {

    public static final int THOST_FTDC_AF_Delete = '0';
    public static final char THOST_FTDC_CC_Immediately = '1';
    public static final char THOST_FTDC_FCC_NotForceClose = '0';
    public static final char THOST_FTDC_HF_Speculation = '1';
    public static final char THOST_FTDC_OPT_LimitPrice = '2';
    public static final char THOST_FTDC_TC_GFD = '3';
    public static final char THOST_FTDC_VC_AV = '1';
    private final AtomicInteger curOrderRef;
    private final CtpTraderGateway gate;
    private ITraderGatewayHandler hnd;
    private final TraderGatewayInfo info;
    private final Map<Long, String> orderIdSysId;
    private final ExecutorService pool;
    private final Properties props;
    private final Map<String, Long> refOrderId;
    private final AtomicInteger requestId;
    private final AtomicInteger status;

    public AbstractCtpTraderSpi(CtpTraderGateway gateway) {
        info = new TraderGatewayInfo();
        gate = gateway;
        pool = Executors.newCachedThreadPool();
        props = new Properties();
        status = new AtomicInteger(GatewayStatus.NEVER_CONNECTED);
        refOrderId = new ConcurrentHashMap<>(1024);
        orderIdSysId = new ConcurrentHashMap<>(1024);
        curOrderRef = new AtomicInteger(0);
        requestId = new AtomicInteger(0);
    }

    private int nextRequestId() {
        return requestId.incrementAndGet();
    }

    int apiAuthenticate() {
        var r = new CThostFtdcReqAuthenticateField();
        r.setAppID(getAppId());
        r.setAuthCode(getAuthCode());
        r.setBrokerID(getBrokerId());
        r.setUserID(getUserId());
        r.setUserProductInfo("COREBOT");
        return gate.getApi().ReqAuthenticate(r,
                                             nextRequestId());
    }

    int apiConfirmSettlement() {
        var r = new CThostFtdcSettlementInfoConfirmField();
        r.setAccountID("");
        r.setBrokerID(getBrokerId());
        r.setConfirmDate("");
        r.setConfirmTime("");
        r.setCurrencyID("CNY");
        r.setInvestorID(getUserId());
        r.setSettlementID(0);
        return gate.getApi().ReqSettlementInfoConfirm(r,
                                                      nextRequestId());
    }

    int apiLogin() {
        var r = new CThostFtdcReqUserLoginField();
        r.setBrokerID(getBrokerId());
        r.setClientIPAddress("");
        r.setClientIPPort(0);
        r.setInterfaceProductInfo("");
        r.setLoginRemark("");
        r.setMacAddress("");
        r.setOneTimePassword("");
        r.setPassword(getPassword());
        r.setProtocolInfo("");
        r.setTradingDay("");
        r.setUserID(getUserId());
        r.setUserProductInfo("");
        return gate.getApi().ReqUserLogin(r,
                                          nextRequestId());
    }

    int apiLogout() {
        var r = new CThostFtdcUserLogoutField();
        r.setBrokerID(getBrokerId());
        r.setUserID(getUserId());
        return gate.getApi().ReqUserLogout(r,
                                           nextRequestId());
    }

    int deleteOrder(Request request,
                    long requestId) throws GatewayException {
        CThostFtdcInputOrderActionField r = new CThostFtdcInputOrderActionField();
        r.setActionFlag((char) THOST_FTDC_AF_Delete);
        r.setBrokerID(getBrokerId());
        r.setExchangeID(request.getExchangeId());
        r.setFrontID(0);
        r.setIPAddress("");
        r.setInstrumentID(request.getInstrumentId());
        r.setInvestUnitID("");
        r.setInvestorID(getUserId());
        r.setLimitPrice(0D);
        r.setMacAddress("");
        r.setOrderActionRef(0);
        r.setOrderRef("");
        r.setOrderSysID(getOrderSysIdByOrderId(request.getOrderId()));
        r.setRequestID((int) requestId);
        r.setSessionID(0);
        r.setUserID(getUserId());
        r.setVolumeChange(0);
        return gate.getApi().ReqOrderAction(r, (int) requestId);
    }

    String getAppId() {
        return props.getProperty("AppId", "");
    }

    String getAuthCode() {
        return props.getProperty("AuthCode", "");
    }

    String getBrokerId() {
        return props.getProperty("BrokerId", "");
    }

    String getFlowPath() {
        return props.getProperty("FlowPath", "");
    }

    Collection<String> getFronts() {
        var r = new HashSet<String>(16);
        props.entrySet().forEach(entry -> {
            String key = (String) entry.getKey();
            if (key.startsWith("Front.")) {
                r.add((String) entry.getKey());
            }
        });
        return r;
    }

    ITraderGatewayHandler getHandler() {
        return hnd;
    }

    void setHandler(ITraderGatewayHandler handler) {
        hnd = handler;
    }

    TraderGatewayInfo getInfo() {
        return info;
    }

    Long getOrderIdByOrderRef(String orderRef) throws GatewayException {
        if (!refOrderId.containsKey(orderRef)) {
            throw new GatewayException(GatewayStatus.INTERNAL_MISSING_INFO,
                                       "Order ID not found for order reference " + orderRef + ".");
        }
        return refOrderId.get(orderRef);
    }

    String getOrderSysIdByOrderId(Long orderId) throws GatewayException {
        if (!orderIdSysId.containsKey(orderId)) {
            throw new GatewayException(GatewayStatus.INTERNAL_MISSING_INFO,
                                       "Order system ID not found for order ID " + orderId + ".");
        }
        return orderIdSysId.get(orderId);
    }

    String getPassword() {
        return props.getProperty("Password", "");
    }

    Properties getProperties() {
        return props;
    }

    void setProperties(Properties properties) {
        props.clear();
        props.putAll(properties);
    }

    int getStatus() {
        return status.get();
    }

    String getUserId() {
        return props.getProperty("UserId", "");
    }

    int insertOrder(Request request,
                    long requestId) throws GatewayException {
        CThostFtdcInputOrderField r = new CThostFtdcInputOrderField();
        r.setAccountID(getUserId());
        r.setBrokerID(getBrokerId());
        r.setBusinessUnit("");
        r.setClientID("");
        r.setCombHedgeFlag(String.valueOf(THOST_FTDC_HF_Speculation));
        r.setCombOffsetFlag(String.valueOf((char) request.getDirection().intValue()));
        r.setContingentCondition(THOST_FTDC_CC_Immediately);
        r.setCurrencyID("CNY");
        r.setDirection((char) request.getDirection().intValue());
        r.setExchangeID(request.getExchangeId());
        r.setForceCloseReason(THOST_FTDC_FCC_NotForceClose);
        r.setGTDDate("");
        r.setIPAddress("");
        r.setInstrumentID(request.getInstrumentId());
        r.setInvestUnitID("");
        r.setInvestorID(getUserId());
        r.setIsAutoSuspend(0);
        r.setIsSwapOrder(0);
        r.setLimitPrice(request.getPrice());
        r.setMacAddress("");
        r.setMinVolume(1);
        r.setOrderPriceType(THOST_FTDC_OPT_LimitPrice);
        r.setOrderRef(nextOrderRefByOrderId(request.getOrderId()));
        r.setRequestID((int) requestId);
        r.setStopPrice(0);
        r.setTimeCondition(THOST_FTDC_TC_GFD);
        r.setUserForceClose(0);
        r.setUserID(getUserId());
        r.setVolumeCondition(THOST_FTDC_VC_AV);
        r.setVolumeTotalOriginal(request.getQuantity().intValue());
        return gate.getApi().ReqOrderInsert(r, (int) requestId);
    }

    String nextOrderRefByOrderId(Long orderId) throws GatewayException {
        var ref = Integer.toString(curOrderRef.incrementAndGet());
        if (refOrderId.containsKey(ref)) {
            throw new GatewayException(GatewayStatus.INTERNAL_COLLISION,
                                       "Duplciated order reference " + ref + ".");
        }
        refOrderId.put(ref,
                       orderId);
        return ref;
    }

    void setOrderSysId(String orderSysId,
                       String orderRef) throws GatewayException {
        orderIdSysId.put(getOrderIdByOrderRef(orderRef),
                         orderSysId);
    }

    void setStatus(int status,
                   String msg) {
        this.status.set(status);
        pool.submit(() -> {
            try {
                gate.getHander().onStatusChange(new ServiceRuntimeStatus(status, msg));
            }
            catch (Throwable ignored) {
            }
        });
    }
}
