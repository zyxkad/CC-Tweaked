/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */
package dan200.computercraft.shared.computer.metrics;

import dan200.computercraft.core.metrics.Metric;
import dan200.computercraft.core.metrics.MetricsObserver;
import dan200.computercraft.shared.computer.core.ServerComputer;

/**
 * A global version of {@link MetricsObserver}, which monitors multiple computers.
 */
public interface ComputerMetricsObserver
{
    /**
     * Increment a computer's counter by 1.
     *
     * @param computer The computer which incremented its counter.
     * @param counter  The counter to observe.
     * @see MetricsObserver#observe(Metric.Counter)
     */
    void observe( ServerComputer computer, Metric.Counter counter );

    /**
     * Observe a single instance of an event.
     *
     * @param computer The computer which incremented its counter.
     * @param event    The event to observe.
     * @param value    The value corresponding to this event.
     * @see MetricsObserver#observe(Metric.Event, long)
     */
    void observe( ServerComputer computer, Metric.Event event, long value );
}
