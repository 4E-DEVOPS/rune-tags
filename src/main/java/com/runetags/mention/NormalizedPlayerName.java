package com.runetags.mention;

import lombok.Value;

@Value
public class NormalizedPlayerName
{
    String canonicalName;
    String comparisonKey;
    String taggedToken;
}
