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
import com.openglobes.core.trader.ITraderGatewayHandler;
import com.openglobes.core.trader.Request;
import com.openglobes.core.trader.Response;
import com.openglobes.core.trader.Trade;
import org.ctp4j.ThostFtdcCtpApi;
import org.junit.jupiter.api.*;

import java.io.IOException;
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

        try {
            gate.start(props, new ITraderGatewayHandler() {
                @Override
                public void onTrade(Trade trade) {
                    System.out.println(Utils.jsonify(trade));
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
                }
            });

            new CountDownLatch(1).await();
        } catch (GatewayException | InterruptedException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}