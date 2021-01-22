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
    public static int NEVER_CONNECTED    = 0x1001;
    public static int CONNECTED          = 0x1002;
    public static int DISCONNECTED       = 0x1003;
    public static int AUTHENTICATED      = 0x1004;
    public static int AUTHENTICATE_FAIL  = 0x1005;
    public static int LOGIN              = 0x1006;
    public static int LOGIN_FAIL         = 0x1007;
    public static int CONFIRM_FAIL       = 0x1008;
    public static int LOGOUT             = 0x1009;
    public static int LOGOUT_FAIL        = 0x100A;
    public static int INTERNAL_MISSED    = 0x2001;
    public static int INTERNAL_COLLISION = 0x2002;
    public static int INTERNAL_UNCAUGHT  = 0x2003;
}
