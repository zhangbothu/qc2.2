package com.mapsug.spell.recall;

import com.mapsug.spell.core.CountryResources;
import com.mapsug.spell.text.Token;

/** One recall channel. Implementations must be thread-safe and allocation-light: they run per keystroke. */
public interface Recaller {

    void recall(Token token, CountryResources res, CandidateSet out);
}
