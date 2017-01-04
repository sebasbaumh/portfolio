package name.abuchen.portfolio.online.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.model.Exchange;
import name.abuchen.portfolio.model.LatestSecurityPrice;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;
import name.abuchen.portfolio.online.QuoteFeed;

/**
 * A quote feed for finanztreff.de.
 * 
 * @author SB
 */
public class FinanztreffDeQuoteFeed implements QuoteFeed
{
    /**
     * Combination of quotes and latest quote.
     */
    private static class CombinedQuoteResult
    {
        private LatestSecurityPrice latest;

        /**
         * All prices.
         */
        public final List<LatestSecurityPrice> prices = new ArrayList<LatestSecurityPrice>();

        /**
         * Gets the latest quote.
         * 
         * @return latest quote on success, else null
         */
        public LatestSecurityPrice getLatest()
        {
            return latest;
        }

        /**
         * Sets the latest quote.
         * 
         * @param latest
         *            latest quote
         */
        public void setLatest(LatestSecurityPrice latest)
        {
            this.latest = latest;
        }
    }

    /**
     * ID of the provider.
     */
    public static final String ID = "FINANZTREFF_DE"; //$NON-NLS-1$
    /**
     * ID of the exchange.
     */
    public static final String ID_EXCHANGE = "FINANZTREFF_DE.SINGLEQUOTE"; //$NON-NLS-1$

    private static final String QUERY_URL = "http://www.finanztreff.de/ftreffNG/ajax/get_search.htn?suchbegriff={isin}"; //$NON-NLS-1$

    /**
     * Collects all query results from the given {@link InputStream}.
     * 
     * @param is
     *            {@link InputStream}
     * @return list of query result URLs
     * @throws IOException
     */
    private static List<String> collectQueryResults(InputStream is) throws IOException
    {
        ArrayList<String> results = new ArrayList<String>();
        // <button type="submit"
        // onclick="location.href='http://etf.finanztreff.de/etf_einzelkurs_uebersicht.htn?i=33851047';">db
        // x-trackers Harvest CSI 300 Index UCITS ETF<span
        // class="typ">ETF</span><span class="wkn">DBX0NK</span></button>
        Pattern pLine = Pattern.compile("<button.*location\\.href='([^']+).*"); //$NON-NLS-1$
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is)))
        {
            String line;
            while ((line = br.readLine()) != null)
            {
                line = line.trim();
                if (!line.isEmpty())
                {
                    Matcher m = pLine.matcher(line);
                    if (m.matches())
                    {
                        // remember url
                        String url = m.group(1);
                        results.add(url);
                    }
                }
            }
        }
        return results;
    }

    /**
     * Gets a {@link Date} value.
     * 
     * @param o
     *            {@link JSONObject}
     * @param key
     *            key
     * @return {@link Date} value on success, else null
     */
    private static LocalDateTime getDate(JSONObject o, String key)
    {
        Long l = getLong(o, key);
        if (l != null)
        {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(l.longValue()), TimeZone.getDefault().toZoneId());
        }
        return null;
    }

    /**
     * Gets a long value.
     * 
     * @param o
     *            {@link JSONObject}
     * @param key
     *            key
     * @return long value on success, else null
     */
    @SuppressWarnings("boxing")
    private static Long getLong(JSONObject o, String key)
    {
        Object value = o.get(key);
        // handle strings
        if (value instanceof String)
        {
            try
            {
                value = Long.parseLong((String) value);
            }
            catch (NumberFormatException ex)
            {}
        }
        if (value instanceof Long)
        {
            return (Long) value;
        }
        return null;
    }

    /**
     * Gets a price as a long value.
     * 
     * @param o
     *            {@link JSONObject}
     * @param key
     *            key
     * @return price on success, else null
     */
    @SuppressWarnings("boxing")
    private static Long getPrice(JSONObject o, String key)
    {
        Object value = o.get(key);
        // handle strings
        if (value instanceof String)
        {
            try
            {
                value = Double.parseDouble((String) value);
            }
            catch (NumberFormatException ex)
            {}
        }
        if (value instanceof Long)
        {
            return ((Long) value) * 100;
        }
        if (value instanceof Double)
        {
            return (long) (Math.round(((Double) value) * 100));
        }
        return null;
    }

    /**
     * Parses the given {@link InputStream} into a list of security prices.
     * 
     * @param is
     *            {@link InputStream}
     * @param s
     *            {@link Security}
     * @param dateStart
     *            start date (can be null)
     * @param errors
     *            errors list
     * @return list of security prices
     * @throws IOException
     */
    protected static CombinedQuoteResult getQuotes(InputStream is, Security s, LocalDate dateStart,
                    List<Exception> errors)
    {
        CombinedQuoteResult result = new CombinedQuoteResult();
        try
        {
            Pattern pLine = Pattern.compile("\\$\\.extend\\(true,\\ss\\.USFdata\\.(\\w+).*"); //$NON-NLS-1$
            Pattern pSingleQuote = Pattern.compile("\\[(\\d+),([0-9.]+)\\]"); //$NON-NLS-1$
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is)))
            {
                String line;
                while ((line = br.readLine()) != null)
                {
                    line = line.trim();
                    if (!line.isEmpty())
                    {
                        Matcher m = pLine.matcher(line);
                        if (m.matches())
                        {
                            // get time range for this block
                            String range = m.group(1);
                            // get additional price information
                            if ("intraday".equalsIgnoreCase(range)) //$NON-NLS-1$
                            {
                                // cut out JSON string and parse it
                                int iJsonStart = line.indexOf('{');
                                int iJsonEnd = line.indexOf('}');
                                String sJson = line.substring(iJsonStart, iJsonEnd + 1);
                                JSONObject ojIntraday = (JSONObject) JSONValue.parse(sJson);
                                if (ojIntraday != null)
                                {
                                    // get individual values
                                    Long low = getPrice(ojIntraday, "low"); //$NON-NLS-1$
                                    Long high = getPrice(ojIntraday, "high"); //$NON-NLS-1$
                                    Long lastPrice = getPrice(ojIntraday, "lastprice"); //$NON-NLS-1$
                                    Long previousClose = getPrice(ojIntraday, "yesterdayPrice"); //$NON-NLS-1$
                                    LocalDate lastTimeStamp = getDate(ojIntraday, "lastTimestamp").toLocalDate(); //$NON-NLS-1$
                                    // if at least time and price are there,
                                    // construct a latest quote
                                    if ((lastTimeStamp != null) && (lastPrice != null))
                                    {
                                        LatestSecurityPrice latest = new LatestSecurityPrice(lastTimeStamp,
                                                        lastPrice.longValue());
                                        // set all known properties
                                        if (low != null)
                                        {
                                            latest.setLow(low.longValue());
                                        }
                                        if (high != null)
                                        {
                                            latest.setHigh(high.longValue());
                                        }
                                        if (previousClose != null)
                                        {
                                            latest.setPreviousClose(previousClose.longValue());
                                        }
                                        // add latest quote to returned result
                                        result.setLatest(latest);
                                    }
                                }
                            }
                            m = pSingleQuote.matcher(line);
                            while (m.find())
                            {
                                String sTime = m.group(1);
                                String sValue = m.group(2);
                                // get a unix timestamp and value
                                long lTime = Long.parseLong(sTime);
                                double dValue = Double.parseDouble(sValue);
                                LocalDate date = LocalDateTime.ofInstant(Instant.ofEpochMilli(lTime),
                                                TimeZone.getDefault().toZoneId()).toLocalDate();
                                // check if start date is given and if so, if
                                // current
                                // date is not before it
                                if ((dateStart == null) || (date.compareTo(dateStart) >= 0))
                                {
                                    result.prices.add(new LatestSecurityPrice(date, (long) (dValue * 100)));
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception ex)
        {
            errors.add(ex);
        }
        return result;
    }

    /**
     * Gets quotes for the given security.
     * 
     * @param is
     *            {@link InputStream}
     * @param s
     *            {@link Security}
     * @param dateStart
     *            start date (can be null)
     * @param errors
     *            errors list
     * @return list of security prices
     */
    protected static CombinedQuoteResult getQuotes(Security s, LocalDate dateStart, List<Exception> errors)
    {
        String isin = s.getIsin();
        if ((isin != null) && !isin.isEmpty())
        {
            List<String> results = null;
            // execute query
            try (InputStream is = openQueryStream(isin))
            {
                results = collectQueryResults(is);
            }
            catch (IOException ex)
            {
                errors.add(ex);
            }
            // check for a single result
            if ((results != null) && (results.size() == 1))
            {
                String url = results.get(0);
                try (InputStream is = new URL(url).openStream())
                {
                    return getQuotes(is, s, dateStart, errors);
                }
                catch (IOException ex)
                {
                    errors.add(ex);
                }
            }
        }
        // return empty result
        return new CombinedQuoteResult();
    }

    /**
     * Open a stream for the given ISIN.
     * 
     * @param isin
     *            ISIN
     * @return {@link InputStream}
     * @throws IOException
     */
    protected static InputStream openQueryStream(String isin) throws IOException
    {
        return new URL(QUERY_URL.replace("{isin}", isin)).openStream(); //$NON-NLS-1$
    }

    @Override
    public List<Exchange> getExchanges(Security subject, List<Exception> errors)
    {
        return null;
    }

    @Override
    public List<LatestSecurityPrice> getHistoricalQuotes(Security security, LocalDate start, List<Exception> errors)
    {
        return getQuotes(security, start, errors).prices;
    }

    @Override
    public List<LatestSecurityPrice> getHistoricalQuotes(String response, List<Exception> errors)
    {
        // not supported
        throw new UnsupportedOperationException();
    }

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public String getName()
    {
        return Messages.LabelFinanztreffDe;
    }

    @Override
    public boolean updateHistoricalQuotes(Security security, List<Exception> errors)
    {
        boolean isUpdated = false;
        CombinedQuoteResult result = getQuotes(security, null, errors);
        for (SecurityPrice p : result.prices)
        {
            if (security.addPrice(p))
            {
                isUpdated = true;
            }
        }
        // get latest price if possible
        LatestSecurityPrice latest = result.getLatest();
        if (latest != null)
        {
            // set it
            if (security.setLatest(latest))
            {
                isUpdated = true;
            }
        }
        return isUpdated;
    }

    @Override
    public boolean updateLatestQuotes(List<Security> securities, List<Exception> errors)
    {
        boolean isUpdated = false;
        for (Security security : securities)
        {
            // get standard quotes
            CombinedQuoteResult result = getQuotes(security, null, errors);
            // get latest price if possible
            LatestSecurityPrice latest = result.getLatest();
            if (latest != null)
            {
                // set it
                if (security.setLatest(latest))
                {
                    isUpdated = true;
                }
            }
        }
        return isUpdated;
    }
}
