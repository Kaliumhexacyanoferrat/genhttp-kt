package org.genhttp.api.content

/**
 * Allows to create a handler instance.
 */
public interface HandlerBuilder {

    /**
     * Creates the configured handler instance.
     *
     * @return The newly created handler instance
     */
    public fun build(): Handler

    /**
     * Typed version of the handler builder (`IHandlerBuilder<TBuilder>` in GenHTTP).
     *
     * Should be used by all handler builder implementations to enable common features on all builders.
     *
     * @param TBuilder The type of builder, so that we can use the builder pattern
     */
    public interface WithConcerns<out TBuilder : WithConcerns<TBuilder>> : HandlerBuilder {

        /**
         * Adds the given concern to the resulting handler.
         *
         * The first concern added to the builder will be the new root of the chain returned by the builder.
         *
         * @param concern The concern to be added to the resulting handler
         */
        public fun add(concern: ConcernBuilder): TBuilder

    }

}
