/*
 * Copyright (c) 2020-2021. Hongbao Chen <chenhongbao@outlook.com>
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
import com.openglobes.core.trader.*;
import org.ctp4j.ThostFtdcCtpApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Order;

import java.io.IOException;
import java.io.StringReader;
import java.time.ZonedDateTime;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("CTP Gateway")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CtpTraderGatewayTest {

    @BeforeEach
    void setUp() {
        try {
            ThostFtdcCtpApi.install();
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @Order(0)
    //@Disabled
    @DisplayName("ITraderGateway::start()")
    public void start() {
        var gate  = new CtpTraderGateway();
        var props = new Properties();

        // Set authentication information.
        props.put("AppId",
                  "3430491819");
        props.put("AuthCode",
                  "0000000000000000");
        // Set account login information.
        props.put("BrokerId",
                  "9999");
        props.put("UserId",
                  "144287");
        props.put("Password",
                  "chb_1987_1013");
        // Set flow cache path.
        props.put("FlowPath",
                  "C:\\Users\\chenh\\Desktop\\gateway\\");
        // Set connected front addresses.
        props.put("Front.1",
                  "tcp://180.168.146.187:10101");
        final var latch = new CountDownLatch(1);
        try {
            gate.setHandler(new ITraderGatewayHandler() {
                private final String exchangeId = "DEC";
                private Long requestId = 0L;
                private Long orderId = 0L;
                @Override
                public void onTrade(Trade trade) {
                    System.out.println(Utils.jsonify(trade));
                    if (trade.getOffset() != Offset.OPEN) {
                        latch.countDown();
                        return;
                    }
                    var r = new Request();
                    r.setAction(ActionType.NEW);
                    r.setDirection(trade.getDirection() == Direction.BUY ? Direction.SELL : Direction.BUY);
                    r.setTraderId(trade.getTraderId());
                    r.setOffset(Offset.CLOSE_TODAY);
                    r.setInstrumentId(trade.getInstrumentId());
                    r.setExchangeId(exchangeId);
                    r.setOrderId(++orderId);
                    r.setPrice(r.getDirection() == Direction.BUY ? trade.getPrice() + 5 : trade.getPrice() - 5);
                    r.setQuantity(1L);
                    r.setRequestId(++requestId);
                    r.setSignature(String.valueOf(r.hashCode()));
                    r.setTag("unit_test");
                    r.setUpdateTimestamp(ZonedDateTime.now());
                    try {
                        gate.insert(r, requestId);
                    } catch (GatewayException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onResponse(Response response) {
                    System.out.println(Utils.jsonify(response));
                }

                @Override
                public void onException(GatewayRuntimeException e) {
                    System.out.println(Utils.jsonify(e));
                }

                @Override
                public void onException(Request request, GatewayRuntimeException e, int i) {
                    System.out.println(Utils.jsonify(request));
                    System.out.println(Utils.jsonify(e));
                }

                @Override
                public void onStatusChange(ServiceRuntimeStatus serviceRuntimeStatus) {
                    System.out.println(Utils.jsonify(serviceRuntimeStatus));
                    if (serviceRuntimeStatus.getCode() == GatewayStatus.CONFIRMED) {
                        var r = new Request();
                        r.setAction(ActionType.NEW);
                        r.setDirection(Direction.BUY);
                        r.setTraderId(0);
                        r.setOffset(Offset.OPEN);
                        r.setInstrumentId("c2109");
                        r.setExchangeId(exchangeId);
                        r.setOrderId(++orderId);
                        r.setPrice(2700D);
                        r.setQuantity(1L);
                        r.setRequestId(++requestId);
                        r.setSignature(String.valueOf(r.hashCode()));
                        r.setTag("unit_test");
                        r.setUpdateTimestamp(ZonedDateTime.now());
                        try {
                            gate.insert(r, requestId);
                        } catch (GatewayException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            gate.start(props);
            latch.await();
        } catch (GatewayException | InterruptedException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}