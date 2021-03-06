package com.fasterxml.jackson.core.filter;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.util.JsonParserDelegate;

/**
 * Specialized {@link JsonParserDelegate} that allows use of
 * {@link TokenFilter} for outputting a subset of content that
 * is visible to caller
 * 
 * @since 2.6
 */
public class FilteringParserDelegate extends JsonParserDelegate
{
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */
    
    /**
     * Object consulted to determine whether to write parts of content generator
     * is asked to write or not.
     */
    protected TokenFilter rootFilter;

    /**
     * Flag that determines whether filtering will continue after the first
     * match is indicated or not: if `false`, output is based on just the first
     * full match (returning {@link TokenFilter#INCLUDE_ALL}) and no more
     * checks are made; if `true` then filtering will be applied as necessary
     * until end of content.
     */
    protected boolean _allowMultipleMatches;

    /**
     * Flag that determines whether path leading up to included content should
     * also be automatically included or not. If `false`, no path inclusion is
     * done and only explicitly included entries are output; if `true` then
     * path from main level down to match is also included as necessary.
     */
    protected boolean _includePath;
    
    /*
    /**********************************************************
    /* State
    /**********************************************************
     */

    /**
     * Last token retrieved via {@link #nextToken}, if any.
     * Null before the first call to <code>nextToken()</code>,
     * as well as if token has been explicitly cleared
     */
    protected JsonToken _currToken;

    /**
     * Last cleared token, if any: that is, value that was in
     * effect when {@link #clearCurrentToken} was called.
     */
    protected JsonToken _lastClearedToken;
    
    /**
     * Although delegate has its own output context it is not sufficient since we actually
     * have to keep track of excluded (filtered out) structures as well as ones delegate
     * actually outputs.
     */
    protected TokenFilterContext _filterContext;

    /**
     * State that applies to the item within container, used where applicable.
     * Specifically used to pass inclusion state between property name and
     * property, and also used for array elements.
     */
    protected TokenFilter _itemFilter;
    
    /**
     * Number of tokens for which {@link TokenFilter#INCLUDE_ALL}
     * has been returned
     */
    protected int _matchCount;

    /*
    /**********************************************************
    /* Construction, initialization
    /**********************************************************
     */

    public FilteringParserDelegate(JsonParser p, TokenFilter f,
            boolean includePath, boolean allowMultipleMatches)
    {
        super(p);
        rootFilter = f;
        // and this is the currently active filter for root values
        _itemFilter = f;
        _filterContext = TokenFilterContext.createRootContext(f);
        _includePath = includePath;
        _allowMultipleMatches = allowMultipleMatches;
    }

    /*
    /**********************************************************
    /* Extended API
    /**********************************************************
     */

    public TokenFilter getFilter() { return rootFilter; }

    /**
     * Accessor for finding number of matches, where specific token and sub-tree
     * starting (if structured type) are passed.
     */
    public int getMatchCount() {
        return _matchCount;
    }

    /*
    /**********************************************************
    /* Public API, token accessors
    /**********************************************************
     */

    @Override public JsonToken getCurrentToken() { return _currToken; }

    @Override public final int getCurrentTokenId() {
        final JsonToken t = _currToken;
        return (t == null) ? JsonTokenId.ID_NO_TOKEN : t.id();
    }

    @Override public boolean hasCurrentToken() { return _currToken != null; }
    @Override public boolean hasTokenId(int id) {
        final JsonToken t = _currToken;
        if (t == null) {
            return (JsonTokenId.ID_NO_TOKEN == id);
        }
        return t.id() == id;
    }

    @Override public final boolean hasToken(JsonToken t) {
        return (_currToken == t);
    }
    
    @Override public boolean isExpectedStartArrayToken() { return _currToken == JsonToken.START_ARRAY; }
    @Override public boolean isExpectedStartObjectToken() { return _currToken == JsonToken.START_OBJECT; }

    @Override public JsonLocation getCurrentLocation() { return delegate.getCurrentLocation(); }
    @Override public JsonStreamContext getParsingContext() { return _filterContext; }

    // !!! TODO: not necessarily correct...
    @Override public String getCurrentName() throws IOException { return delegate.getCurrentName(); }
    
    /*
    /**********************************************************
    /* Public API, token state overrides
    /**********************************************************
     */

    @Override
    public void clearCurrentToken() {
        if (_currToken != null) {
            _lastClearedToken = _currToken;
            _currToken = null;
        }
    }

    @Override
    public JsonToken getLastClearedToken() { return _lastClearedToken; }

    // !!! TODO: re-implement
    @Override
    public void overrideCurrentName(String name) { delegate.overrideCurrentName(name); }

    /*
    /**********************************************************
    /* Public API, traversal
    /**********************************************************
     */

    // !!! TODO: re-implement
    @Override public JsonToken nextToken() throws IOException { return delegate.nextToken(); }

    @Override
    public JsonToken nextValue() throws IOException {
        // Re-implemented same as ParserMinimalBase:
        JsonToken t = nextToken();
        if (t == JsonToken.FIELD_NAME) {
            t = nextToken();
        }
        return t;
    }

    /**
     * Need to override, re-implement similar to how method defined in
     * {@link com.fasterxml.jackson.core.base.ParserMinimalBase}, to keep
     * state correct here.
     */
    @Override
    public JsonParser skipChildren() throws IOException
    {
        if (_currToken != JsonToken.START_OBJECT
            && _currToken != JsonToken.START_ARRAY) {
            return this;
        }
        int open = 1;

        // Since proper matching of start/end markers is handled
        // by nextToken(), we'll just count nesting levels here
        while (true) {
            JsonToken t = nextToken();
            if (t == null) { // not ideal but for now, just return
                return this;
            }
            if (t.isStructStart()) {
                ++open;
            } else if (t.isStructEnd()) {
                if (--open == 0) {
                    return this;
                }
            }
        }
    }
    
    /*
    /**********************************************************
    /* Public API, access to token information, text
    /**********************************************************
     */

    @Override public String getText() throws IOException { return delegate.getText();  }
    @Override public boolean hasTextCharacters() { return delegate.hasTextCharacters(); }
    @Override public char[] getTextCharacters() throws IOException { return delegate.getTextCharacters(); }
    @Override public int getTextLength() throws IOException { return delegate.getTextLength(); }
    @Override public int getTextOffset() throws IOException { return delegate.getTextOffset(); }

    /*
    /**********************************************************
    /* Public API, access to token information, numeric
    /**********************************************************
     */
    
    @Override
    public BigInteger getBigIntegerValue() throws IOException { return delegate.getBigIntegerValue(); }

    @Override
    public boolean getBooleanValue() throws IOException { return delegate.getBooleanValue(); }
    
    @Override
    public byte getByteValue() throws IOException { return delegate.getByteValue(); }

    @Override
    public short getShortValue() throws IOException { return delegate.getShortValue(); }

    @Override
    public BigDecimal getDecimalValue() throws IOException { return delegate.getDecimalValue(); }

    @Override
    public double getDoubleValue() throws IOException { return delegate.getDoubleValue(); }

    @Override
    public float getFloatValue() throws IOException { return delegate.getFloatValue(); }

    @Override
    public int getIntValue() throws IOException { return delegate.getIntValue(); }

    @Override
    public long getLongValue() throws IOException { return delegate.getLongValue(); }

    @Override
    public NumberType getNumberType() throws IOException { return delegate.getNumberType(); }

    @Override
    public Number getNumberValue() throws IOException { return delegate.getNumberValue(); }

    /*
    /**********************************************************
    /* Public API, access to token information, coercion/conversion
    /**********************************************************
     */
    
    @Override public int getValueAsInt() throws IOException { return delegate.getValueAsInt(); }
    @Override public int getValueAsInt(int defaultValue) throws IOException { return delegate.getValueAsInt(defaultValue); }
    @Override public long getValueAsLong() throws IOException { return delegate.getValueAsLong(); }
    @Override public long getValueAsLong(long defaultValue) throws IOException { return delegate.getValueAsLong(defaultValue); }
    @Override public double getValueAsDouble() throws IOException { return delegate.getValueAsDouble(); }
    @Override public double getValueAsDouble(double defaultValue) throws IOException { return delegate.getValueAsDouble(defaultValue); }
    @Override public boolean getValueAsBoolean() throws IOException { return delegate.getValueAsBoolean(); }
    @Override public boolean getValueAsBoolean(boolean defaultValue) throws IOException { return delegate.getValueAsBoolean(defaultValue); }
    @Override public String getValueAsString() throws IOException { return delegate.getValueAsString(); }
    @Override public String getValueAsString(String defaultValue) throws IOException { return delegate.getValueAsString(defaultValue); }
    
    /*
    /**********************************************************
    /* Public API, access to token values, other
    /**********************************************************
     */

    @Override public Object getEmbeddedObject() throws IOException { return delegate.getEmbeddedObject(); }
    @Override public byte[] getBinaryValue(Base64Variant b64variant) throws IOException { return delegate.getBinaryValue(b64variant); }
    @Override public int readBinaryValue(Base64Variant b64variant, OutputStream out) throws IOException { return delegate.readBinaryValue(b64variant, out); }
    @Override public JsonLocation getTokenLocation() { return delegate.getTokenLocation(); }
}
