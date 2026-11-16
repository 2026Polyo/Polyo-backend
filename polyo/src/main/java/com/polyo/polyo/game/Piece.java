package com.polyo.polyo.game;

import java.util.List;

/** A block in the shared tray. {@code special} is null for a plain block. Cells are [row, col] offsets. */
public record Piece(String uid, String id, List<int[]> cells, String color, String special) {
    public boolean is(String type) {
        return type.equals(special);
    }
}
