package org.github.legioth.servertiming;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.DataProviderWrapper;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServletResponse;

/**
 * Sends server-side timing information as a header in the current response.
 * <p>
 * The timing information is sent as <code>Server-Timing</code> headers in the
 * response. The browser will typically make that information available in the
 * networking section of their developer tools.
 * <p>
 * The information should typically be provided using one of the static factory
 * methods such as {@link #set(String)} or {@link #start(String)} or by wrapping
 * a callback with methods such as
 * {@link #wrapListener(String, ComponentEventListener)},
 * {@link #wrapDataProvider(String, DataProvider)},
 * {@link #run(String, Command)} or {@link #supply(String, Supplier)}. Using the
 * {@link #ServerTiming(String)} constructor to manually create an entry and
 * submitting it using {@link #submit(VaadinServletResponse)} or
 * {@link #forceSubmit(VaadinServletResponse)} is mainly intended for advanced
 * use cases.
 * <p>
 * By default, timing information is ignored when the application is in
 * production mode. This is to reduce response sizes and to prevent leaking
 * potentially sensitive information to attackers. Custom logic for determining
 * whether timing information should be sent can be defined using
 * {@link #setEnabledCheck(EnabledCheck)}.
 */
/*-
 * TODO
 * - Enforce token / quoted-string based on the spec
 * - Use UI.beforeClientResponse if there is no response right now - might not make sense because of websockets
 */
public class ServerTiming {

    /*
     * Globally shared no-op stopwatch instance that is used whenever server
     * timing sending is disabled.
     */
    private static final Stopwatch NOP_STOPWATCH = () -> {
        // no op
    };

    /**
     * The default callback for testing whether server timing submitting is
     * enabled.
     * <p>
     * Submitting is enabled if there is a {@link VaadinService#getCurrent()}
     * and that service has production mode disabled.
     */
    public static final EnabledCheck DEFAULT_ENABLED_CHECK = () -> {
        VaadinService service = VaadinService.getCurrent();

        return service != null && !service.getDeploymentConfiguration().isProductionMode();
    };

    private static EnabledCheck enabledCheck = DEFAULT_ENABLED_CHECK;

    /**
     * Callback interface used to determine whether sending of server timing
     * information is enabled. Use
     * {@link ServerTiming#setEnabledCheck(EnabledCheck)} to supply a custom
     * implementation.
     */
    @FunctionalInterface
    public interface EnabledCheck {
        /**
         * Run to check whether to send server timing information. An
         * implementation would typically use globally available information
         * such as one of Vaadin's thread local variables to determine the
         * outcome.
         * 
         * @return <code>true</code> if server timing information should be sent
         *         based on the current context, otherwise <code>false</code>
         */
        boolean isEnabled();
    }

    /**
     * Measures the time it takes to complete an operation and submits a server
     * timing entry when the operation completes. An instance is retrieved using
     * {@link ServerTiming#start(String)}. Call {@link #complete()} to submit
     * the information.
     */
    @FunctionalInterface
    public interface Stopwatch {
        /**
         * Marks the completion of the task that is being timed. This records
         * the time elapsed since this stopwatch was being started (using
         * {@link ServerTiming#start(String)}) and submits a server timing entry
         * for the current request.
         * <p>
         * Calling this method multiple times will cause multiple entries to be
         * submitted with the same name. The stopwatch duration is not reset
         * between invocations.
         * <p>
         * This method has no effect if server timing was not enabled at the
         * time when the stopwatch was started.
         */
        void complete();
    }

    private final String name;
    private Map<String, String> parameters;

    /**
     * Creates a new server timing entry with the given name.
     * 
     * @param name
     *            the name of this server timing entry, not <code>null</code>
     */
    public ServerTiming(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Sets the duration in milliseconds of this server timing entry.
     * 
     * @param duration
     *            the duration in milliseconds to set
     * @return this entry, for chaining
     */
    public ServerTiming setDuration(double duration) {
        return setParameter("dur", Double.toString(duration));
    }

    /**
     * Adds an arbitrary parameter value for this server timing entry.
     * 
     * @param name
     *            the name of the parameter to set, not <code>null</code>
     * @param value
     *            the value to set, not <code>null</code>
     * @return this entry, for chaining
     */
    public ServerTiming setParameter(String name, String value) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }

        parameters.put(Objects.requireNonNull(name), Objects.requireNonNull(value));
        return this;
    }

    /**
     * Sends a server timing entry with a name but no duration. No entry is sent
     * if server timing is disabled.
     * 
     * @param name
     *            the name of the entry to send, not <code>null</code>
     */
    public static void set(String name) {
        if (enabledCheck.isEnabled()) {
            new ServerTiming(name).forceSubmit();
        }
    }

    /**
     * Sends a server timing entry with the given name and duration. No entry is
     * sent if server timing is disabled.
     * 
     * @param name
     *            the name of the entry to send, not <code>null</code>
     * @param duration
     *            the duration of the entry in milliseconds
     */
    public static void set(String name, double duration) {
        if (enabledCheck.isEnabled()) {
            new ServerTiming(name).setDuration(duration).forceSubmit();
        }
    }

    /**
     * Starts a stopwatch that will send a server timing entry when it is
     * completed. Completing the stopwatch will not send any entry if server
     * timing is disabled when this method is run.
     * 
     * @param name
     *            the name of the server timing entry that should be set when
     *            the stopwatch is completed, not <code>null</code>
     * @return a stopwatch instance that is used to send the entry when the
     *         action is completed
     */
    public static Stopwatch start(String name) {
        if (!enabledCheck.isEnabled()) {
            return NOP_STOPWATCH;
        }

        return forceStart(name);
    }

    /**
     * Starts a stopwatch that will submit an entry regardless of whether server
     * timing is enabled. This is used internally by methods that already check
     * the enabled status prior to starting a stopwatch.
     * 
     * @param name
     *            the name of the server timing entry to create, not
     *            <code>null</code>
     * @return a stopwatch that will always submit an entry when completed, not
     *         <code>null</code>
     */
    private static Stopwatch forceStart(String name) {
        long start = System.nanoTime();
        return () -> {
            ServerTiming timingEntry = new ServerTiming(name);
            timingEntry.setDuration((System.nanoTime() - start) / 1e6d);
            timingEntry.forceSubmit();
        };
    }

    /**
     * Runs the given command immediately and submits a server timing entry
     * based on the duration of executing the command.
     * 
     * @param name
     *            the name of the server timing entry to submit, not
     *            <code>null</code>
     * @param command
     *            the command to run, not <code>null</code>
     */
    public static void run(String name, Command command) {
        if (!enabledCheck.isEnabled()) {
            command.execute();
        }

        Stopwatch stopwatch = forceStart(name);
        command.execute();
        stopwatch.complete();
    }

    /**
     * Runs the given supplier immediately and submits a server timing entry
     * based on the duration of running the supplier.
     * 
     * @param name
     *            the name of the server timing entry to submit, not
     *            <code>null</code>
     * @param supplier
     *            the supplier to run, not <code>null</code>
     * @param <T>
     *            the supplier type
     * @return the value provided by the supplier
     */
    public static <T> T supply(String name, Supplier<T> supplier) {
        if (!enabledCheck.isEnabled()) {
            return supplier.get();
        }

        Stopwatch stopwatch = forceStart(name);
        try {
            return supplier.get();
        } finally {
            stopwatch.complete();
        }
    }

    /**
     * Wraps a component event listener to create a listener that submits a
     * server timing entry based on the duration of running the wrapped
     * listener. The original listener is returned without wrapping if server
     * timing is disabled when this method is run.
     * 
     * @param name
     *            the name of the server timing entry to submit, not
     *            <code>null</code>
     * @param listener
     *            the component event listener to wrap
     * @param <T>
     *            the event type of the listener
     * @return a wrapping component event listener, or the original listener if
     *         server timing is disabled
     */
    public static <T extends ComponentEvent<?>> ComponentEventListener<T> wrapListener(String name,
            ComponentEventListener<T> listener) {
        if (!enabledCheck.isEnabled()) {
            return listener;
        }

        return event -> {
            Stopwatch stopwatch = forceStart(name);
            listener.onComponentEvent(event);
            stopwatch.complete();
        };
    }

    /**
     * Wraps a data provider to create a data provider that submits a server
     * timing entry based on the duration of fetching or counting items in the
     * wrapped data provider. The original data provider is returned without
     * wrapping if server timing is disabled when this method is run.
     * 
     * @param name
     *            the name of the server timing entry to submit, not
     *            <code>null</code>
     * @param provider
     *            the data provider to wrap, not <code>null</code>
     * @param <T>
     *            the data provider item type
     * @param <F>
     *            the data provider filter type
     * @return a wrapping data provider , or the original data provider if
     *         server timing is disabled
     */
    public static <T, F> DataProvider<T, F> wrapDataProvider(String name, DataProvider<T, F> provider) {
        if (!enabledCheck.isEnabled()) {
            return provider;
        }

        return new DataProviderWrapper<T, F, F>(provider) {
            @Override
            protected F getFilter(Query<T, F> query) {
                return query.getFilter().orElse(null);
            }

            @Override
            public Stream<T> fetch(Query<T, F> t) {
                Stopwatch stopwatch = forceStart(name + ".fetch");
                try {
                    return super.fetch(t);
                } finally {
                    stopwatch.complete();
                }
            }

            @Override
            public int size(Query<T, F> t) {
                Stopwatch stopwatch = forceStart(name + ".size");
                try {
                    return super.size(t);
                } finally {
                    stopwatch.complete();
                }
            }
        };
    }

    private void forceSubmit() {
        forceSubmit(getCurrentResponse());
    }

    private static VaadinServletResponse getCurrentResponse() {
        VaadinResponse response = VaadinResponse.getCurrent();
        if (response instanceof VaadinServletResponse) {
            return (VaadinServletResponse) response;
        } else if (response == null) {
            throw new IllegalStateException("Cannot submit a server timing if there isn't a current Vaadin response");
        } else {
            /*
             * VaadinResponse doesn't define addHeader. We could collect
             * multiple timing entries into one header, but there isn't any
             * (good) place to collect them either since getHeader is also
             * missing. One potential approach would be to store the temporary
             * information as an attribute on the corresponding request, but
             * that's boilerplate code that I'll consider only once there is at
             * least one non-servlet user of this add-on.
             */

            throw new IllegalStateException("Server timings can only be set for servlet responses.");
        }
    }

    /**
     * Submits this server timing entry by adding a header to the provided
     * response. The header is added only if server entry submitting is enabled.
     * 
     * @param response
     *            the Vaadin servlet response to add a header to, not
     *            <code>null</code>
     */
    public void submit(VaadinServletResponse response) {
        assert response != null;

        if (!enabledCheck.isEnabled()) {
            return;
        }

        forceSubmit(response);
    }

    /**
     * Submits this server timing entry regardless of whether server entry
     * submitting is enabled.
     * 
     * @param response
     *            the Vaadin servlet response to add a header to, not
     *            <code>null</code>
     */
    public void forceSubmit(VaadinServletResponse response) {
        StringBuilder headerValue = new StringBuilder(name);

        if (parameters != null) {
            parameters.forEach((key, value) -> headerValue.append(';').append(key).append('=').append(value));
        }

        response.addHeader("Server-Timing", headerValue.toString());
    }

    /**
     * Sets a custom enabled check callback that will be used to determine
     * whether server timing entries should actually be submitted. The default
     * implementation enables submitting when production mode is disabled.
     * <p>
     * Note that this method does not perform any synchronization. For this
     * reason, it is recommended to only use this method when the servlet
     * context is being deployed.
     * 
     * @param enabledCheck
     *            the enabled check callback to set, not <code>null</code>
     */
    public static void setEnabledCheck(EnabledCheck enabledCheck) {
        ServerTiming.enabledCheck = Objects.requireNonNull(enabledCheck);
    }
}
