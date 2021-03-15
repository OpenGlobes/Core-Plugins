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

/**
 * @author Hongbao Chen
 * @since 1.0
 */
public class GatewayStatus {

    public static int NO_ERROR           = 0;
    public static int NEVER_CONNECTED    = 1;
    public static int CONNECTED          = 2;
    public static int DISCONNECTED       = 3;
    public static int AUTHENTICATED      = 4;
    public static int AUTHENTICATE_FAIL  = 5;
    public static int LOGIN              = 6;
    public static int LOGIN_FAIL         = 7;
    public static int CONFIRMED          = 8;
    public static int CONFIRM_FAIL       = 9;
    public static int LOGOUT             = 10;
    public static int LOGOUT_FAIL        = 11;
    public static int INTERNAL_MISSED    = 12;
    public static int INTERNAL_COLLISION = 13;
    public static int INTERNAL_UNCAUGHT  = 14;
}
