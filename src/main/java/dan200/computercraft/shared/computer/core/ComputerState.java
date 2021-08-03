/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2021. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */
package dan200.computercraft.shared.computer.core;

import net.minecraft.util.StringRepresentable;

import javax.annotation.Nonnull;

public enum ComputerState implements StringRepresentable
{
    OFF( "off" ),
    ON( "on" ),
    BLINKING( "blinking" );

    private final String name;

    ComputerState( String name )
    {
        this.name = name;
    }

    @Nonnull
    @Override
    public String getSerializedName()
    {
        return name;
    }

    @Override
    public String toString()
    {
        return name;
    }
}
