import de.mossgrabers.framework.controller.hardware.AbstractHwButton;
import de.mossgrabers.framework.controller.hardware.BindType;
import de.mossgrabers.framework.controller.hardware.IHwLight;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.utils.ButtonEvent;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Regression tests for the real DrivenByMoss hardware-button event guard. */
public final class AbstractHwButtonSafetyTest
{
    private AbstractHwButtonSafetyTest () { }

    public static void main (final String [] args)
    {
        final TestHost host = new TestHost ();
        final TestButton button = new TestButton (host.host ());

        button.trigger (ButtonEvent.DOWN);
        button.trigger (ButtonEvent.DOWN);
        require (button.count (ButtonEvent.DOWN) == 1 && button.isPressed (),
            "Duplicate DOWN edges must not execute twice");

        button.trigger (ButtonEvent.UP);
        button.trigger (ButtonEvent.UP);
        require (button.count (ButtonEvent.UP) == 1 && !button.isPressed (),
            "Duplicate UP edges must not execute twice");

        // No time-based lockout: every complete physical tap is intentional,
        // even when taps are adjacent in the same event-loop turn.
        final int taps = 10000;
        for (int i = 0; i < taps; i++)
        {
            button.trigger (ButtonEvent.DOWN);
            button.trigger (ButtonEvent.UP);
        }
        require (button.count (ButtonEvent.DOWN) == taps + 1,
            "Rapid complete taps were lost or coalesced");
        require (button.count (ButtonEvent.UP) == taps + 1,
            "Rapid complete releases were lost or coalesced");

        // A delayed callback from press A must not turn press B into LONG.
        final TestButton longButton = new TestButton (host.host ());
        longButton.trigger (ButtonEvent.DOWN);
        final Runnable stalePress = host.lastScheduled ();
        longButton.trigger (ButtonEvent.UP);
        longButton.trigger (ButtonEvent.DOWN);
        final Runnable currentPress = host.lastScheduled ();
        stalePress.run ();
        require (!longButton.isLongPressed () && longButton.count (ButtonEvent.LONG) == 0,
            "A stale long-press callback changed a later press");
        currentPress.run ();
        require (longButton.isLongPressed () && longButton.count (ButtonEvent.LONG) == 1,
            "The current press did not receive exactly one LONG event");
        currentPress.run ();
        require (longButton.count (ButtonEvent.LONG) == 1,
            "Duplicate scheduler delivery emitted LONG more than once");
        longButton.trigger (ButtonEvent.UP);

        // clearState invalidates pending callbacks (mode/surface reset case).
        longButton.trigger (ButtonEvent.DOWN);
        final Runnable clearedPress = host.lastScheduled ();
        longButton.clearState ();
        clearedPress.run ();
        require (!longButton.isPressed () && longButton.count (ButtonEvent.LONG) == 1,
            "A callback survived clearState and resurrected a cleared press");

        // State is per physical button; simultaneous chords remain independent.
        final TestButton first = new TestButton (host.host ());
        final TestButton second = new TestButton (host.host ());
        first.trigger (ButtonEvent.DOWN);
        second.trigger (ButtonEvent.DOWN);
        require (first.isPressed () && second.isPressed (),
            "Simultaneous button presses interfered with one another");
        first.trigger (ButtonEvent.UP);
        require (!first.isPressed () && second.isPressed (),
            "Releasing one chord member changed another button's state");
        second.trigger (ButtonEvent.UP);

        System.out.println ("Abstract hardware button safety passed: duplicate edges, 10,000 rapid taps, stale/duplicate long-press callbacks, state reset and simultaneous chords.");
    }

    private static void require (final boolean condition, final String message)
    {
        if (!condition)
            throw new AssertionError (message);
    }

    private static final class TestButton extends AbstractHwButton
    {
        private final List<ButtonEvent> events = new ArrayList<> ();

        TestButton (final IHost host)
        {
            super (host, "safety-test");
            this.command = (event, velocity) -> this.events.add (event);
        }

        @Override public void bind (final de.mossgrabers.framework.command.core.TriggerCommand command) { this.command = command; }
        @Override public void bind (final IMidiInput input, final BindType type, final int channel, final int control) { }
        @Override public void bind (final IMidiInput input, final BindType type, final int channel, final int control, final int value) { }
        @Override public void unbind () { }
        @Override public void rebind () { }
        @Override public void setBounds (final double x, final double y, final double width, final double height) { }

        int count (final ButtonEvent event)
        {
            return (int) this.events.stream ().filter (value -> value == event).count ();
        }
    }

    private static final class TestHost implements InvocationHandler
    {
        private final List<Runnable> scheduled = new ArrayList<> ();
        private final IHost proxy = (IHost) Proxy.newProxyInstance (
            IHost.class.getClassLoader (), new Class<?> [] { IHost.class }, this);

        IHost host () { return this.proxy; }

        Runnable lastScheduled ()
        {
            return this.scheduled.get (this.scheduled.size () - 1);
        }

        @Override
        public Object invoke (final Object proxyObject, final Method method, final Object [] args)
        {
            if ("scheduleTask".equals (method.getName ()))
            {
                this.scheduled.add ((Runnable) args[0]);
                return null;
            }
            if ("toString".equals (method.getName ()))
                return "ButtonSafetyTestHost";
            if ("hashCode".equals (method.getName ()))
                return Integer.valueOf (System.identityHashCode (proxyObject));
            if ("equals".equals (method.getName ()))
                return Boolean.valueOf (proxyObject == args[0]);
            final Class<?> type = method.getReturnType ();
            if (!type.isPrimitive () || type == Void.TYPE)
                return null;
            if (type == Boolean.TYPE)
                return Boolean.FALSE;
            if (type == Character.TYPE)
                return Character.valueOf ('\0');
            if (type == Long.TYPE)
                return Long.valueOf (0);
            if (type == Float.TYPE)
                return Float.valueOf (0);
            if (type == Double.TYPE)
                return Double.valueOf (0);
            if (type == Byte.TYPE)
                return Byte.valueOf ((byte) 0);
            if (type == Short.TYPE)
                return Short.valueOf ((short) 0);
            return Integer.valueOf (0);
        }
    }
}
