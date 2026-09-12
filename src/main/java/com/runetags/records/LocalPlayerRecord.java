package com.runetags.records;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * Persistent user-owned metadata for one RuneScape player.
 *
 * This record stores durable RuneTags metadata separately from live observed
 * RuneLite player state.
 *
 * Previous RSNs are retained as history only and are not used as lookup aliases,
 * since released RuneScape names may later belong to another account.
 */
@Value
@Builder(toBuilder = true)
public class LocalPlayerRecord
{
    String currentRsn;

    @Builder.Default
    List<String> previousRsns =
            Collections.emptyList();

    boolean favorite;

    String note;

    @Builder.Default
    List<String> tags =
            Collections.emptyList();
}
