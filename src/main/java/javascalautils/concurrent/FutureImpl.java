/**
 * Copyright 2015 Peter Nerg
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package javascalautils.concurrent;

import static javascalautils.Option.None;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javascalautils.Failure;
import javascalautils.Option;
import javascalautils.Success;
import javascalautils.Try;

/**
 * The future implementation.
 * 
 * @author Peter Nerg
 * @since 1.2
 */
final class FutureImpl<T> implements Future<T> {
    /**
     * Contains either a {@link Success} as a result of {@link #success(Object)} or a {@link Failure} as a result of {@link #failure(Throwable)}
     */
    private Option<Try<T>> response = None();

    /** The success handlers set by the user. */
    private final List<EventHandler<T>> successHandlers = new ArrayList<>();

    /** The failure handlers set by the user. */
    private final List<EventHandler<Throwable>> failureHandlers = new ArrayList<>();

    /** The complete handlers set by the user. */
    private final List<EventHandler<Try<T>>> completeHandlers = new ArrayList<>();

    @Override
    public boolean isCompleted() {
        return response.isDefined();
    }

    @Override
    public Option<Try<T>> value() {
        return response;
    }

    @Override
    public void onFailure(Consumer<Throwable> c) {
        failureHandlers.add(new EventHandler<>(c));
        response.filter(Try::isFailure).map(Try::failed).map(Try::orNull).forEach(t -> notifyHandlers(failureHandlers, t));
    }

    @Override
    public void onSuccess(Consumer<T> c) {
        successHandlers.add(new EventHandler<>(c));
        response.filter(Try::isSuccess).map(Try::orNull).forEach(r -> notifyHandlers(successHandlers, r));
    }

    /*
     * (non-Javadoc)
     * 
     * @see javascalautils.concurrent.Future#onComplete(java.util.function.Consumer)
     */
    @Override
    public void onComplete(Consumer<Try<T>> c) {
        completeHandlers.add(new EventHandler<>(c));
        response.forEach(t -> notifyHandlers(completeHandlers, t));
    }

    /**
     * Used to report a success to this future.
     * 
     * @param value
     *            The response value
     */
    void success(final T value) {
        Try<T> success = new Success<>(value);
        this.response = Option.apply(success);

        notifyHandlers(successHandlers, value);
        notifyHandlers(completeHandlers, success);
    }

    /**
     * Used to report a failure to this future.
     * 
     * @param throwable
     *            The failure Throwable
     */
    void failure(Throwable throwable) {
        Try<T> failure = new Failure<>(throwable);
        this.response = Option.apply(failure);

        notifyHandlers(failureHandlers, throwable);
        notifyHandlers(completeHandlers, failure);
    }

    /**
     * Invoke all provided handlers with the provided value. <br>
     * A filter is applied to make sure we only notify handlers that have not been notified before.
     * 
     * @param handlers
     *            The handlers to notify.
     * @param value
     */
    private <R> void notifyHandlers(List<EventHandler<R>> handlers, R value) {
        // The filter is to make sure we only respond/notify once
        handlers.stream().filter(h -> !h.notified()).forEach(h -> h.notify(value));
    }

    /**
     * Internal holder for the success/failure handlers provided by the user.<br>
     * Used primarily to keep track on if a particular handler already has been notified. <br>
     * This is to make sure the same handler won't be notified more than once.
     * 
     * @author Peter Nerg
     *
     * @param <R>
     */
    private static final class EventHandler<R> {
        private final Consumer<R> consumer;
        private boolean notified = false;

        private EventHandler(Consumer<R> consumer) {
            this.consumer = consumer;
        }

        /**
         * If this event handler already has been notified.
         * 
         * @return
         */
        private boolean notified() {
            return notified;
        }

        /**
         * Notifies the response to the handler.
         * 
         * @param response
         */
        private void notify(R response) {
            notified = true;
            consumer.accept(response);
        }
    }
}
