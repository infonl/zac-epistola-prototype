/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.model

/** A job that is not finished yet: waiting for a free render slot at Epistola, or being rendered. */
enum class EpistolaJobStatus {
    WAITING_IN_QUEUE,
    HELD_UP_IN_QUEUE,
    RENDERING,
    HELD_UP_IN_RENDERING
}
