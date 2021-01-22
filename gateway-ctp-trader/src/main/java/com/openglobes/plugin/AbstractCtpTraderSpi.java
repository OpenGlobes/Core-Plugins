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
import com.openglobes.core.GatewayRuntimeException;
import com.openglobes.core.ServiceRuntimeStatus;
import com.openglobes.core.trader.ITraderGatewayHandler;
import com.openglobes.core.trader.Request;
import com.openglobes.core.trader.Response;
import com.openglobes.core.trader.Trade;
import com.openglobes.core.trader.TraderGatewayInfo;
import com.openglobes.core.utils.Utils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.ctp4j.CThostFtdcInputOrderActionField;
import org.ctp4j.CThostFtdcInputOrderField;
import org.ctp4j.CThostFtdcOrderActionField;
import org.ctp4j.CThostFtdcOrderField;
import org.ctp4j.CThostFtdcReqAuthenticateField;
import org.ctp4j.CThostFtdcReqUserLoginField;
import org.ctp4j.CThostFtdcRspInfoField;
import org.ctp4j.CThostFtdcRspUserLoginField;
import org.ctp4j.CThostFtdcSettlementInfoConfirmField;
import org.ctp4j.CThostFtdcTradeField;
import org.ctp4j.CThostFtdcTraderSpi;
import org.ctp4j.CThostFtdcUserLogoutField;

/**
 * @author Hongbao Chen
 * @since 1.0
 */
public class AbstractCtpTraderSpi extends CThostFtdcTraderSpi {

    public static final int                   THOST_FTDC_AF_Delete         = '0';
    public static final char                  THOST_FTDC_CC_Immediately    = '1';
    public static final char                  THOST_FTDC_FCC_NotForceClose = '0';
    public static final char                  THOST_FTDC_HF_Speculation    = '1';
    public static final char                  THOST_FTDC_OPT_LimitPrice    = '2';
    public static final char                  THOST_FTDC_TC_GFD            = '3';
    public static final char                  THOST_FTDC_VC_AV             = '1';
    private final       AtomicInteger         curOrderRef;
    private final       DateTimeFormatter     dayFormatter;
    private final       CtpTraderGateway      gate;
    private final       TraderGatewayInfo     info;
    private final       Map<Long, String>     orderIdSysId;
    private final       ExecutorService       pool;
    private final       Properties            props;
    private final       Map<String, Long>     refOrderId;
    private final       AtomicInteger         requestId;
    private final       Map<Long, Request>    requests;
    private final       AtomicInteger         status;
    private final       Map<String, Long>     sysIdOrderId;
    private final       DateTimeFormatter     timeFormatter;
    private             ITraderGatewayHandler hnd;

    public AbstractCtpTraderSpi(CtpTraderGateway gateway) {
        info          = new TraderGatewayInfo();
        gate          = gateway;
        pool          = Executors.newCachedThreadPool();
        props         = new Properties();
        status        = new AtomicInteger(GatewayStatus.NEVER_CONNECTED);
        refOrderId    = new ConcurrentHashMap<>(1024);
        orderIdSysId  = new ConcurrentHashMap<>(1024);
        sysIdOrderId  = new ConcurrentHashMap<>(1024);
        curOrderRef   = new AtomicInteger(0);
        requestId     = new AtomicInteger(0);
        requests      = new ConcurrentHashMap<>(1024);
        dayFormatter  = DateTimeFormatter.ofPattern("yyyyMMdd");
        timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss");
    }

    private void clearObsoletedCache() {
        requests.clear();
        refOrderId.clear();
        orderIdSysId.clear();
        sysIdOrderId.clear();
    }

    private Request getRequestByOrderId(Long orderId) throws GatewayException {
        if (!requests.containsKey(orderId)) {
            throw new GatewayException(GatewayStatus.INTERNAL_MISSED,
                                       "No request for order ID " + orderId + ".");
        }
        return requests.get(orderId);
    }

    private int nextRequestId() {
        return requestId.incrementAndGet();
    }

    private void saveRequest(Request request) throws GatewayException {
        if (requests.containsKey(request.getOrderId())) {
            throw new GatewayException(GatewayStatus.INTERNAL_COLLISION,
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

    void doError(CThostFtdcRspInfoField info) {
        pool.submit(() -> {
            try {
                gate.getHandler().onException(new GatewayRuntimeException(info.getErrorID(),
                                                                          info.getErrorMsg()));
            } catch (Throwable ignored) {
            }
        });
    }

    void doError(CThostFtdcOrderActionField rsp,
                 CThostFtdcRspInfoField info) {
        pool.submit(() -> {
            try {
                gate.getHandler().onException(getRequestByOrderId(getOrderIdBySysId(rsp.getOrderSysID())),
                                              new GatewayRuntimeException(info.getErrorID(),
                                                                         info.getErrorMsg()),
                                              rsp.getRequestID());
            } catch (Throwable th) {
                gate.getHandler().onException(new GatewayRuntimeException(GatewayStatus.INTERNAL_UNCAUGHT,
                                                                          th.getMessage()));
            }
        });
    }

    void doError(CThostFtdcInputOrderField rsp,
                 CThostFtdcRspInfoField info) {
        pool.submit(() -> {
            try {
                gate.getHandler().onException(getRequestByOrderId(getOrderIdByOrderRef(rsp.getOrderRef())),
                                              new GatewayRuntimeException(info.getErrorID(),
                                                                         info.getErrorMsg()),
                                              rsp.getRequestID());
            } catch (Throwable th) {
                gate.getHandler().onException(new GatewayRuntimeException(GatewayStatus.INTERNAL_UNCAUGHT,
                                                                          th.getMessage()));
            }
        });
    }

    void doError(CThostFtdcInputOrderActionField rsp,
                 CThostFtdcRspInfoField info,
                 int requestId) {
        pool.submit(() -> {
            try {
                gate.getHandler().onException(getRequestByOrderId(getOrderIdByOrderRef(rsp.getOrderRef())),
                                              new GatewayRuntimeException(info.getErrorID(),
                                                                         info.getErrorMsg()),
                                              requestId);
            } catch (Throwable th) {
                gate.getHandler().onException(new GatewayRuntimeException(GatewayStatus.INTERNAL_UNCAUGHT,
                                                                          th.getMessage()));
            }
        });
    }

    void doOrder(CThostFtdcOrderField order) {
        pool.submit(() -> {
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
                gate.getHandler().onException(new GatewayRuntimeException(GatewayStatus.INTERNAL_UNCAUGHT,
                                                                          th.getMessage()));
            }
        });
    }

    void doTrade(CThostFtdcTradeField trade) {
        pool.submit(() -> {
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
                t.setTimestamp(getTimestamp(trade.getTradeDate(),
                                            trade.getTradeTime()));
                t.setTradeId(Utils.nextId());
                t.setTraderId(q.getTraderId());
                t.setTradingDay(LocalDate.parse(trade.getTradingDay(),
                                                dayFormatter));
                gate.getHandler().onTrade(t);
            } catch (Throwable th) {
                gate.getHandler().onException(new GatewayRuntimeException(GatewayStatus.INTERNAL_UNCAUGHT,
                                                                          th.getMessage()));
            }
        });
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
        var tradingDay = LocalDate.parse(rsp.getTradingDay(),
                                         dayFormatter);
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

    Long getOrderIdByOrderRef(String orderRef) throws GatewayException {
        if (!refOrderId.containsKey(orderRef)) {
            throw new GatewayException(GatewayStatus.INTERNAL_MISSED,
                                       "Order ID not found for order reference " + orderRef + ".");
        }
        return refOrderId.get(orderRef);
    }

    Long getOrderIdBySysId(String sysId) throws GatewayException {
        if (!sysIdOrderId.containsKey(sysId)) {
            throw new GatewayException(GatewayStatus.INTERNAL_MISSED,
                                       "Order ID not found for system ID " + sysId + ".");
        }
        return sysIdOrderId.get(sysId);
    }

    String getOrderSysIdByOrderId(Long orderId) throws GatewayException {
        if (!orderIdSysId.containsKey(orderId)) {
            throw new GatewayException(GatewayStatus.INTERNAL_MISSED,
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
        LocalTime lt = LocalTime.parse(time,
                                       timeFormatter);
        return ZonedDateTime.of(LocalDate.now(),
                                lt,
                                ZoneId.systemDefault());
    }

    ZonedDateTime getTimestamp(String day, String time) {
        LocalTime lt = LocalTime.parse(time,
                                       timeFormatter);
        LocalDate ld = LocalDate.parse(day,
                                       dayFormatter);
        return ZonedDateTime.of(ld,
                                lt,
                                ZoneId.systemDefault());
    }

    String getUserId() {
        return props.getProperty("UserId", "");
    }

    int insertOrder(Request request,
                    long requestId) throws GatewayException {
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
                gate.getHandler().onStatusChange(new ServiceRuntimeStatus(status, msg));
            } catch (Throwable ignored) {
            }
        });
    }

}
