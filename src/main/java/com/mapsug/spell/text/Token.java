package com.mapsug.spell.text;

/**
 * One token of the user input.
 *
 * @param raw      text exactly as typed
 * @param norm     folded key (see {@link TextNormalizer#fold})
 * @param start    start offset in the original query
 * @param end      end offset (exclusive)
 * @param complete false only for the last token when the user has not typed a separator after
 *                 it yet; such a token may be an unfinished prefix and must stay a prefix
 * @param hasDigit true when the token contains digits (house numbers, postcodes, "km 12")
 */
public record Token(String raw, String norm, int start, int end, boolean complete, boolean hasDigit) {

    public int length() {
        return norm.length();
    }
}
