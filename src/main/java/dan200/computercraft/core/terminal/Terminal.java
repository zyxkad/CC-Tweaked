/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */
package dan200.computercraft.core.terminal;

import dan200.computercraft.shared.util.Colour;
import dan200.computercraft.shared.util.Palette;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public class Terminal
{
    protected static final String BASE_16 = "0123456789abcdef";

    protected int width;
    protected int height;
    protected final boolean colour;

    protected int cursorX = 0;
    protected int cursorY = 0;
    protected boolean cursorBlink = false;
    protected int cursorColour = 0;
    protected int cursorBackgroundColour = 15;

    protected TextBuffer[] text;
    protected TextBuffer[] textColour;
    protected TextBuffer[] backgroundColour;

    protected final Palette palette;

    private final @Nullable Runnable onChanged;

    public Terminal( int width, int height, boolean colour )
    {
        this( width, height, colour, null );
    }

    public Terminal( int width, int height, boolean colour, Runnable changedCallback )
    {
        this.width = width;
        this.height = height;
        this.colour = colour;
        palette = new Palette( colour );
        onChanged = changedCallback;

        text = new TextBuffer[height];
        textColour = new TextBuffer[height];
        backgroundColour = new TextBuffer[height];
        for( int i = 0; i < this.height; i++ )
        {
            text[i] = new TextBuffer( ' ', this.width );
            textColour[i] = new TextBuffer( BASE_16.charAt( cursorColour ), this.width );
            backgroundColour[i] = new TextBuffer( BASE_16.charAt( cursorBackgroundColour ), this.width );
        }
    }

    public synchronized void reset()
    {
        cursorColour = 0;
        cursorBackgroundColour = 15;
        cursorX = 0;
        cursorY = 0;
        cursorBlink = false;
        clear();
        setChanged();
        palette.resetColours();
    }

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }

    public boolean isColour()
    {
        return colour;
    }

    public synchronized void resize( int width, int height )
    {
        if( width == this.width && height == this.height )
        {
            return;
        }

        int oldHeight = this.height;
        int oldWidth = this.width;
        TextBuffer[] oldText = text;
        TextBuffer[] oldTextColour = textColour;
        TextBuffer[] oldBackgroundColour = backgroundColour;

        this.width = width;
        this.height = height;

        text = new TextBuffer[height];
        textColour = new TextBuffer[height];
        backgroundColour = new TextBuffer[height];
        for( int i = 0; i < this.height; i++ )
        {
            if( i >= oldHeight )
            {
                text[i] = new TextBuffer( ' ', this.width );
                textColour[i] = new TextBuffer( BASE_16.charAt( cursorColour ), this.width );
                backgroundColour[i] = new TextBuffer( BASE_16.charAt( cursorBackgroundColour ), this.width );
            }
            else if( this.width == oldWidth )
            {
                text[i] = oldText[i];
                textColour[i] = oldTextColour[i];
                backgroundColour[i] = oldBackgroundColour[i];
            }
            else
            {
                text[i] = new TextBuffer( ' ', this.width );
                textColour[i] = new TextBuffer( BASE_16.charAt( cursorColour ), this.width );
                backgroundColour[i] = new TextBuffer( BASE_16.charAt( cursorBackgroundColour ), this.width );
                text[i].write( oldText[i] );
                textColour[i].write( oldTextColour[i] );
                backgroundColour[i].write( oldBackgroundColour[i] );
            }
        }
        setChanged();
    }

    public void setCursorPos( int x, int y )
    {
        if( cursorX != x || cursorY != y )
        {
            cursorX = x;
            cursorY = y;
            setChanged();
        }
    }

    public void setCursorBlink( boolean blink )
    {
        if( cursorBlink != blink )
        {
            cursorBlink = blink;
            setChanged();
        }
    }

    public void setTextColour( int colour )
    {
        if( cursorColour != colour )
        {
            cursorColour = colour;
            setChanged();
        }
    }

    public void setBackgroundColour( int colour )
    {
        if( cursorBackgroundColour != colour )
        {
            cursorBackgroundColour = colour;
            setChanged();
        }
    }

    public int getCursorX()
    {
        return cursorX;
    }

    public int getCursorY()
    {
        return cursorY;
    }

    public boolean getCursorBlink()
    {
        return cursorBlink;
    }

    public int getTextColour()
    {
        return cursorColour;
    }

    public int getBackgroundColour()
    {
        return cursorBackgroundColour;
    }

    @Nonnull
    public Palette getPalette()
    {
        return palette;
    }

    public synchronized void blit( ByteBuffer text, ByteBuffer textColour, ByteBuffer backgroundColour )
    {
        int x = cursorX;
        int y = cursorY;
        if( y >= 0 && y < height )
        {
            this.text[y].write( text, x );
            this.textColour[y].write( textColour, x );
            this.backgroundColour[y].write( backgroundColour, x );
            setChanged();
        }
    }

    public synchronized void write( String text )
    {
        int x = cursorX;
        int y = cursorY;
        if( y >= 0 && y < height )
        {
            this.text[y].write( text, x );
            textColour[y].fill( BASE_16.charAt( cursorColour ), x, x + text.length() );
            backgroundColour[y].fill( BASE_16.charAt( cursorBackgroundColour ), x, x + text.length() );
            setChanged();
        }
    }

    public synchronized void scroll( int yDiff )
    {
        if( yDiff != 0 )
        {
            TextBuffer[] newText = new TextBuffer[height];
            TextBuffer[] newTextColour = new TextBuffer[height];
            TextBuffer[] newBackgroundColour = new TextBuffer[height];
            for( int y = 0; y < height; y++ )
            {
                int oldY = y + yDiff;
                if( oldY >= 0 && oldY < height )
                {
                    newText[y] = text[oldY];
                    newTextColour[y] = textColour[oldY];
                    newBackgroundColour[y] = backgroundColour[oldY];
                }
                else
                {
                    newText[y] = new TextBuffer( ' ', width );
                    newTextColour[y] = new TextBuffer( BASE_16.charAt( cursorColour ), width );
                    newBackgroundColour[y] = new TextBuffer( BASE_16.charAt( cursorBackgroundColour ), width );
                }
            }
            text = newText;
            textColour = newTextColour;
            backgroundColour = newBackgroundColour;
            setChanged();
        }
    }

    public synchronized void clear()
    {
        for( int y = 0; y < height; y++ )
        {
            text[y].fill( ' ' );
            textColour[y].fill( BASE_16.charAt( cursorColour ) );
            backgroundColour[y].fill( BASE_16.charAt( cursorBackgroundColour ) );
        }
        setChanged();
    }

    public synchronized void clearLine()
    {
        int y = cursorY;
        if( y >= 0 && y < height )
        {
            text[y].fill( ' ' );
            textColour[y].fill( BASE_16.charAt( cursorColour ) );
            backgroundColour[y].fill( BASE_16.charAt( cursorBackgroundColour ) );
            setChanged();
        }
    }

    public synchronized TextBuffer getLine( int y )
    {
        if( y >= 0 && y < height )
        {
            return text[y];
        }
        return null;
    }

    public synchronized void setLine( int y, String text, String textColour, String backgroundColour )
    {
        this.text[y].write( text );
        this.textColour[y].write( textColour );
        this.backgroundColour[y].write( backgroundColour );
        setChanged();
    }

    public synchronized TextBuffer getTextColourLine( int y )
    {
        if( y >= 0 && y < height )
        {
            return textColour[y];
        }
        return null;
    }

    public synchronized TextBuffer getBackgroundColourLine( int y )
    {
        if( y >= 0 && y < height )
        {
            return backgroundColour[y];
        }
        return null;
    }

    public final void setChanged()
    {
        if( onChanged != null ) onChanged.run();
    }

    public static int getColour( char c, Colour def )
    {
        if( c >= '0' && c <= '9' ) return c - '0';
        if( c >= 'a' && c <= 'f' ) return c - 'a' + 10;
        if( c >= 'A' && c <= 'F' ) return c - 'A' + 10;
        return 15 - def.ordinal();
    }
}
