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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Hongbao Chen
 * @since 1.0
 */
public class ConstantMaps {

    private final static Map<Integer, Character> directions = new ConcurrentHashMap<>(4);
    private final static Map<Character, Integer> directions2 = new ConcurrentHashMap<>(4);
    private final static Map<Integer, Character> offsets = new ConcurrentHashMap<>(4);
    private final static Map<Character, Integer> offsets2 = new ConcurrentHashMap<>(4);
    private final static Map<Integer, Character> orderStatuses = new ConcurrentHashMap<>(8);
    private final static Map<Character, Integer> orderStatuses2 = new ConcurrentHashMap<>(8);

    public static Character getDestinatedDirection(Integer localDirection) {
        return directions.get(localDirection);
    }

    // TODO initialize offsets and directions
    public static Character getDestinatedOffset(Integer localOffset) {
        return offsets.get(localOffset);
    }

    public static Integer getLocalDirection(Character destinatedDirection) {
        return directions2.get(destinatedDirection);
    }

    public static Integer getLocalOffset(Character destinatedOffset) {
        return offsets2.get(destinatedOffset);
    }
    
    public static Character getDestinatedOrderStatus(Integer localOrderStatus) {
        return orderStatuses.get(localOrderStatus);
    }
    
    public static Integer getLocalOrderStatus(Character destinatedOrderStatus) {
        return orderStatuses2.get(destinatedOrderStatus);
    }

    private ConstantMaps() {
    }
}
