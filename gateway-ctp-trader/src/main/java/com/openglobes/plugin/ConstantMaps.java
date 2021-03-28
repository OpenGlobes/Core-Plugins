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
import com.openglobes.core.trader.Direction;
import com.openglobes.core.trader.Offset;
import com.openglobes.core.trader.OrderStatus;

/**
 * @author Hongbao Chen
 * @since 1.0
 */
public class ConstantMaps {

    private ConstantMaps() {
    }

    public static Character getDestinatedDirection(Integer localDirection) {
        switch (localDirection) {
            case Direction.BUY:
                return '0';
            case Direction.SELL:
                return '1';
            default:
                throw new GatewayRuntimeException(GatewayStatus.INTERNAL_MISSED,
                                                  "Unknown local direction " + localDirection + ".");
        }
    }

    public static Character getDestinatedOffset(Integer localOffset) {
        switch (localOffset) {
            case Offset.OPEN:
                return '0';
            case Offset.CLOSE_AUTO:
                return '1';
            case Offset.CLOSE_TODAY:
                return '3';
            case Offset.CLOSE_YD:
                return '4';
            default:
                throw new GatewayRuntimeException(GatewayStatus.INTERNAL_MISSED,
                                                  "Unknown local offset " + localOffset + ".");
        }
    }

    public static Integer getLocalOrderStatus(Character destinatedOrderStatus) {
        switch (destinatedOrderStatus) {
            case '0':
                return OrderStatus.ALL_TRADED;
            case '1':
                return OrderStatus.QUEUED;
            case '3':
                return OrderStatus.ACCEPTED;
            case '2':
            case '4':
                return OrderStatus.UNQUEUED;
            default:
                return OrderStatus.DELETED;
        }
    }
}
