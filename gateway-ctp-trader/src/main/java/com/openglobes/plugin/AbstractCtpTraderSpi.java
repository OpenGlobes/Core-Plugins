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
import com.openglobes.core.ServiceRuntimeStatus;
import com.openglobes.core.trader.*;
import com.openglobes.core.utils.Utils;
import org.ctp4j.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
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
    private final DateTimeFormatter dayFormatter;
    private final CtpTraderGateway gate;
    private final TraderGatewayInfo info;
    private final Map<Long, String> orderIdSysId;
    private final Properties props;
    private final Map<String, Long> refOrderId;
    private final AtomicInteger requestId;
    private final Map<Long, Request> requests;
    private final AtomicInteger status;
    private final Map<String, Long> sysIdOrderId;
    private final DateTimeFormatter timeFormatter;
    private ITraderGatewayHandler hnd;

    public AbstractCtpTraderSpi(CtpTraderGateway gateway) {
        info = new TraderGatewayInfo();
        gate = gateway;
        props = new Properties();
        status = new AtomicInteger(GatewayStatus.NEVER_CONNECTED);
        refOrderId = new ConcurrentHashMap<>(1024);
        orderIdSysId = new ConcurrentHashMap<>(1024);
        sysIdOrderId = new ConcurrentHashMap<>(1024);
        curOrderRef = new AtomicInteger(0);
        requestId = new AtomicInteger(0);
        requests = new ConcurrentHashMap<>(1024);
        dayFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    }

    private void clearObsoletedCache() {
        requests.clear();
        refOrderId.clear();
        orderIdSysId.clear();
        sysIdOrderId.clear();
    }

    private Request getRequestByOrderId(Long orderId) {
        if (!requests.containsKey(orderId)) {
            throw new GatewayRuntimeException(GatewayStatus.INTERNAL_MISSED,
                                              "No request for order ID " + orderId + ".");
        }
        return requests.get(orderId);
    }

    private int nextRequestId() {
        return requestId.incrementAndGet();
    }

    private void saveRequest(Request request) {
        if (requests.containsKey(request.getOrderId())) {
            throw new GatewayRuntimeException(GatewayStatus.INTERNAL_COLLISION,
                                              "Duplicated order ID " + request.getOrderId() + ".");
        }
        requests.put(request.getOrderId(),
                     request);
    }

    int apiAuthenticate() {
        var r = new CThostFtdcReqAuthenticateField();
        r.setAppID(getAppId());
        r.setAuthCode(getAuthCode());
        r.setBrokerID(getBrokerId());
        r.setUserID(getUserId());
        r.setUserProductInfo("COREBOT");
        return gate.getApi().ReqAuthenticate(r, nextRequestId());
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
        return gate.getApi().ReqSettlementInfoConfirm(r, nextRequestId());
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
        return gate.getApi().ReqUserLogin(r, nextRequestId());
    }

    int apiLogout() {
        var r = new CThostFtdcUserLogoutField();
        r.setBrokerID(getBrokerId());
        r.setUserID(getUserId());
        return gate.getApi().ReqUserLogout(r, nextRequestId());
    }

    int deleteOrder(Request request) {
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
        r.setRequestID(request.getRequestId().intValue());
        r.setSessionID(0);
        r.setUserID(getUserId());
        r.setVolumeChange(0);
        return gate.getApi().ReqOrderAction(r, request.getRequestId().intValue());
    }

    void doError(CThostFtdcRspInfoField info) {
        try {
            gate.getHandler()
                .onError(new GatewayRuntimeException(info.getErrorID(), info.getErrorMsg()));
        } catch (Throwable ignored) {
        }
    }

    void doError(CThostFtdcOrderActionField rsp,
                 CThostFtdcRspInfoField info) {
        try {
            var request = getRequestByOrderId(getOrderIdBySysId(rsp.getOrderSysID()));
            var response = createErrorResponse(request, info);
            gate.getHandler().onError(request, response);
        } catch (Throwable th) {
            gate.getHandler().onError(
                    new GatewayRuntimeException(GatewayStatus.INTERNAL_UNCAUGHT, th.getMessage()));
        }
    }

    private Response createErrorResponse(Request request, CThostFtdcRspInfoField info) {
        var r = new Response();
        r.setTraderId(request.getTraderId());
        r.setStatus(OrderStatus.REJECTED);
        r.setSignature(UUID.randomUUID().toString());
        r.setTimestamp(ZonedDateTime.now());
        r.setOffset(request.getOffset());
        r.setStatusCode(info.getErrorID());
        r.setStatusMessage(info.getErrorMsg());
        r.setTradingDay(request.getTradingDay());
        r.setOrderId(request.getOrderId());
        r.setInstrumentId(request.getInstrumentId());
        r.setDirection(request.getDirection());
        r.setResponseId(Utils.nextId());
        return r;
    }

    void doError(CThostFtdcInputOrderField rsp,
                 CThostFtdcRspInfoField info) {
        try {
            var request = getRequestByOrderId(getOrderIdByOrderRef(rsp.getOrderRef()));
            var response = createErrorResponse(request, info);
            gate.getHandler().onError(request, response);
        } catch (Throwable th) {
            gate.getHandler().onError(
                    new GatewayRuntimeException(GatewayStatus.INTERNAL_UNCAUGHT, th.getMessage()));
        }
    }

    void doError(CThostFtdcInputOrderActionField rsp,
                 CThostFtdcRspInfoField info,
                 int requestId) {
        try {
            var request = getRequestByOrderId(getOrderIdByOrderRef(rsp.getOrderRef()));
            var response = createErrorResponse(request, info);
            gate.getHandler().onError(request, response);
        } catch (Throwable th) {
            gate.getHandler().onError(
                    new GatewayRuntimeException(GatewayStatus.INTERNAL_UNCAUGHT, th.getMessage()));
        }
    }

    void doOrder(CThostFtdcOrderField order) {
        try {
            var q = getRequestByOrderId(getOrderIdByOrderRef(order.getOrderRef()));
            var r = new Response();
            r.setAction(q.getAction());
            r.setDirection(q.getDirection());
            r.setInstrumentId(q.getInstrumentId());
            r.setOffset(q.getOffset());
            r.setOrderId(q.getOrderId());
            r.setResponseId(Utils.nextId());
            r.setSignature(Utils.nextUuid().toString());
            r.setStatus(ConstantMaps.getLocalOrderStatus(order.getOrderStatus()));
            r.setStatusCode(0);
            r.setStatusMessage(order.getStatusMsg());
            r.setTimestamp(getTimestamp(order.getUpdateTime()));
            r.setTraderId(q.getTraderId());
            r.setTradingDay(LocalDate.parse(order.getTradingDay(),
                                            dayFormatter));
            gate.getHandler().onResponse(r);
        } catch (Throwable th) {
            gate.getHandler().onError(
                    new GatewayRuntimeException(GatewayStatus.INTERNAL_UNCAUGHT, th.getMessage()));
        }
    }

    void doTrade(CThostFtdcTradeField trade) {
        try {
            var q = getRequestByOrderId(getOrderIdByOrderRef(trade.getOrderRef()));
            var t = new Trade();
            t.setAction(q.getAction());
            t.setDirection(q.getDirection());
            t.setInstrumentId(q.getInstrumentId());
            t.setOffset(q.getOffset());
            t.setOrderId(q.getOrderId());
            t.setPrice(trade.getPrice());
            t.setQuantity((long) trade.getVolume());
            t.setSignature(Utils.nextUuid().toString());
            t.setTimestamp(getTimestamp(trade.getTradeDate(), trade.getTradeTime()));
            t.setTradeId(Utils.nextId());
            t.setTraderId(q.getTraderId());
            t.setTradingDay(LocalDate.parse(trade.getTradingDay(), dayFormatter));
            gate.getHandler().onTrade(t);
        } catch (Throwable th) {
            gate.getHandler().onError(
                    new GatewayRuntimeException(GatewayStatus.INTERNAL_UNCAUGHT, th.getMessage()));
        }
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
                r.add((String) entry.getValue());
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

    void setInfo(CThostFtdcRspUserLoginField rsp) {
        var tradingDay = LocalDate.parse(rsp.getTradingDay(), dayFormatter);
        if (info.getTradingDay() != null
            && !info.getTradingDay().equals(tradingDay)) {
            /*
             * Clean cache at the begin of a new trading day.
             */
            clearObsoletedCache();
        }
        info.setActionDay(LocalDate.now());
        info.setTradingDay(tradingDay);
        info.setUpdateTimestamp(ZonedDateTime.now());
        curOrderRef.set(Integer.parseInt(rsp.getMaxOrderRef()));
    }

    Long getOrderIdByOrderRef(String orderRef) {
        if (!refOrderId.containsKey(orderRef)) {
            throw new GatewayRuntimeException(GatewayStatus.INTERNAL_MISSED,
                                              "Order ID not found for order reference " + orderRef + ".");
        }
        return refOrderId.get(orderRef);
    }

    Long getOrderIdBySysId(String sysId) {
        if (!sysIdOrderId.containsKey(sysId)) {
            throw new GatewayRuntimeException(GatewayStatus.INTERNAL_MISSED,
                                              "Order ID not found for system ID " + sysId + ".");
        }
        return sysIdOrderId.get(sysId);
    }

    String getOrderSysIdByOrderId(Long orderId) {
        if (!orderIdSysId.containsKey(orderId)) {
            throw new GatewayRuntimeException(GatewayStatus.INTERNAL_MISSED,
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

    ZonedDateTime getTimestamp(String time) {
        if (time.isBlank()) {
            return ZonedDateTime.now();
        }
        LocalTime lt = LocalTime.parse(time, timeFormatter);
        return ZonedDateTime.of(LocalDate.now(), lt, ZoneId.systemDefault());
    }

    ZonedDateTime getTimestamp(String day, String time) {
        LocalTime lt = LocalTime.parse(time, timeFormatter);
        LocalDate ld = LocalDate.parse(day, dayFormatter);
        return ZonedDateTime.of(ld, lt, ZoneId.systemDefault());
    }

    String getUserId() {
        return props.getProperty("UserId", "");
    }

    int insertOrder(Request request) {
        saveRequest(request);
        /*
         * Translate local request.
         */
        CThostFtdcInputOrderField r = new CThostFtdcInputOrderField();
        r.setAccountID(getUserId());
        r.setBrokerID(getBrokerId());
        r.setBusinessUnit("");
        r.setClientID("");
        r.setCombHedgeFlag(String.valueOf(THOST_FTDC_HF_Speculation));
        r.setCombOffsetFlag(String.valueOf(ConstantMaps.getDestinatedOffset(request.getOffset())));
        r.setContingentCondition(THOST_FTDC_CC_Immediately);
        r.setCurrencyID("CNY");
        r.setDirection(ConstantMaps.getDestinatedDirection(request.getDirection()));
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
        r.setRequestID(request.getRequestId().intValue());
        r.setStopPrice(0);
        r.setTimeCondition(THOST_FTDC_TC_GFD);
        r.setUserForceClose(0);
        r.setUserID(getUserId());
        r.setVolumeCondition(THOST_FTDC_VC_AV);
        r.setVolumeTotalOriginal(request.getQuantity().intValue());
        return gate.getApi().ReqOrderInsert(r, request.getRequestId().intValue());
    }

    String nextOrderRefByOrderId(Long orderId) {
        var ref = Integer.toString(curOrderRef.incrementAndGet());
        if (refOrderId.containsKey(ref)) {
            throw new GatewayRuntimeException(GatewayStatus.INTERNAL_COLLISION,
                                              "Duplicated order reference " + ref + ".");
        }
        refOrderId.put(ref, orderId);
        return ref;
    }

    void setOrderSysId(String orderSysId, String orderRef) {
        orderIdSysId.put(getOrderIdByOrderRef(orderRef), orderSysId);
    }

    void setStatus(int status, String msg) {
        this.status.set(status);
        try {
            gate.getHandler().onStatusChange(new ServiceRuntimeStatus(status, msg));
        } catch (Throwable ignored) {
        }
    }

}
