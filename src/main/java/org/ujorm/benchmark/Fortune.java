package org.ujorm.benchmark;

/** Represents a fortune message entity */
public record Fortune(
        /** Returns the unique identifier of the fortune */
        int id,

        /** Returns the message content of the fortune */
        String message
) {
}