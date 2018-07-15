package name.abuchen.portfolio.online.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.online.QuoteFeed;

/**
 * A quote feed for finanztreff.de.
 *
 * @author Sebastian Baumhekel
 */
public class FinanztreffDeQuoteFeed implements QuoteFeed
{
    /**
     * Combination of quotes and latest quote.
     */
    private static class CombinedQuoteResult
    {
        /**
         * Latest quote.
         */
        public LatestSecurityPrice latest;

        /**
         * All prices.
         */
        public final List<LatestSecurityPrice> prices = new ArrayList<LatestSecurityPrice>();
    }

    /**
     * ID of the provider.
     */
    public static final String ID = "FINANZTREFF_DE"; //$NON-NLS-1$
    /**
     * ID of the exchange.
     */
    public static final String ID_EXCHANGE = "FINANZTREFF_DE.SINGLEQUOTE"; //$NON-NLS-1$
    private static final String QUERY_URL = "http://www.finanztreff.de/ftreffNG/ajax/get_search.htn?suchbegriff={key}"; //$NON-NLS-1$
    private static final String REFERRER_URL = "http://www.finanztreff.de"; //$NON-NLS-1$

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
        Pattern pLine = Pattern.compile("location\\.href='([^']+)"); //$NON-NLS-1$
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is)))
        {
            String line;
            while ((line = br.readLine()) != null)
            {
                line = line.trim();
                if (!line.isEmpty())
                {
                    Matcher m = pLine.matcher(line);
                    while (m.find())
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
     * Collects all available quote pages.
     *
     * @param lines
     *            lines
     * @return quote pages
     */
    private static List<String> collectQuotePages(Iterable<String> lines)
    {
        // USFchangeSnap[^ ]+ href="([^"]+)
        Pattern pLine = Pattern.compile("USFchangeSnap[^ ]+ href=\"([^\"]+)"); //$NON-NLS-1$
        ArrayList<String> results = new ArrayList<String>();
        for (String line : lines)
        {
            Matcher m = pLine.matcher(line);
            while (m.find())
            {
                results.add(m.group(1));
            }
        }
        return results;
    }

    /**
     * Gets the currency from the given data.
     *
     * @param lines
     *            lines
     * @return currency code like "USD" on success, else null
     */
    private static String getCurrency(Iterable<String> lines)
    {
        // <div class="whrg" title="US-Dollar">Währung: USD</div>
        Pattern pCur = Pattern.compile("<div class=\"whrg\" [^>]+>[^:]+: ([A-Z]+)<"); //$NON-NLS-1$
        for (String line : lines)
        {
            Matcher m = pCur.matcher(line);
            if (m.find()) { return m.group(1); }
        }
        return null;
    }

    /**
     * Gets a {@link LocalDate} value.
     *
     * @param o
     *            {@link JSONObject}
     * @param key
     *            key
     * @return {@link LocalDate} value on success, else null
     */
    private static LocalDate getDate(JSONObject o, String key)
    {
        Long l = getLong(o, key);
        if (l != null) { return LocalDateTime
                        .ofInstant(Instant.ofEpochMilli(l.longValue()), TimeZone.getDefault().toZoneId())
                        .toLocalDate(); }
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
        if (value instanceof Long) { return (Long) value; }
        return null;
    }

    /**
     * Gets a price as a long value.
     *
     * @param o
     *            {@link JSONObject}
     * @param key
     *            key
     * @return price on success, else -1
     */
    private static long getPrice(JSONObject o, String key)
    {
        return getPrice(o.get(key));
    }

    /**
     * Gets a price as a long value.
     *
     * @param value
     *            value
     * @return price on success, else -1
     */
    @SuppressWarnings("boxing")
    private static long getPrice(Object value)
    {
        if (value != null)
        {
            double v = Double.NaN;
            // handle strings
            if (value instanceof String)
            {
                try
                {
                    v = Double.parseDouble((String) value);
                }
                catch (NumberFormatException ex)
                {}
            }
            else if (value instanceof Long)
            {
                v = (Long) value;
            }
            else if (value instanceof Double)
            {
                v = (Double) value;
            }
            // if value is found, adjust it and return it
            if (!Double.isNaN(v)) { return Math.round(v * Values.Quote.factor()); }
        }
        return -1;
    }

    /**
     * Gets the content of the web page for the given {@link Security}.
     * 
     * @param s
     *            {@link Security}
     * @param forQuotes
     *            return content for getting quotes? (should match currency and
     *            selected exchange)
     * @param errors
     *            errors list
     * @return content lines on success, else null
     */
    private static List<String> getContentOfSecurityPage(Security s, boolean forQuotes, List<Exception> errors)
    {
        // get content for quotes then check for exchange
        if (forQuotes)
        {
            // check if ticker symbol is there
            String exchangeUrl = s.getTickerSymbol();
            if ((exchangeUrl != null) && !exchangeUrl.isEmpty())
            {
                // just retrieve exactly this url
                try (InputStream is = openUrlStream(new URL(exchangeUrl), null))
                {
                    // read data to lines
                    return readToLines(is);
                }
                catch (MalformedURLException ex)
                {
                    // ignore if this is not an actual URL
                }
                catch (IOException ex)
                {
                    errors.add(ex);
                }
            }
        }
        // retrieve data using the ISIN?
        String key = s.getIsin();
        if ((key == null) || key.isEmpty())
        {
            // then try WKN
            key = s.getWkn();
            if ((key == null) || key.isEmpty())
            {
                // then ticker symbol
                key = s.getTickerSymbol();
            }
        }
        if ((key != null) && !key.isEmpty())
        {
            List<String> results = null;
            // execute query
            String sQueryUrl = QUERY_URL.replace("{key}", key); //$NON-NLS-1$
            try (InputStream is = openUrlStream(new URL(sQueryUrl), REFERRER_URL))
            {
                results = collectQueryResults(is);
            }
            catch (IOException ex)
            {
                errors.add(ex);
            }
            // get first result
            if ((results != null) && (!results.isEmpty()))
            {
                String url = results.get(0);
                List<String> lines = null;
                try (InputStream is = openUrlStream(new URL(url), sQueryUrl))
                {
                    // cache data
                    lines = readToLines(is);
                }
                catch (IOException ex)
                {
                    errors.add(ex);
                }

                // try to match currency of security?
                if ((lines != null) && forQuotes)
                {
                    // check if currency matches
                    String securityCurrency = s.getCurrencyCode();
                    String currency = getCurrency(lines);
                    if ((currency != null) && !currency.equals(securityCurrency))
                    {
                        // collect all available pages
                        List<String> lQuoteUrls = collectQuotePages(lines);
                        // try to get the correct currency page
                        if (!lQuoteUrls.isEmpty())
                        {
                            for (String sQuoteUrl : lQuoteUrls)
                            {
                                List<String> lines2 = null;
                                try (InputStream is = openUrlStream(new URL(sQuoteUrl), url))
                                {
                                    // cache data
                                    lines2 = readToLines(is);
                                    String currency2 = getCurrency(lines2);
                                    // if currency matches, use that page data
                                    if (securityCurrency.equals(currency2))
                                    {
                                        lines = lines2;
                                        break;
                                    }
                                }
                                catch (IOException ex)
                                {
                                    errors.add(ex);
                                }
                            }
                        }
                    }
                }
                return lines;
            }
        }
        return null;
    }

    /**
     * Gets quotes for the given security.
     *
     * @param s
     *            {@link Security}
     * @param dateStart
     *            start date (can be null)
     * @param errors
     *            errors list
     * @return list of security prices
     */
    private static CombinedQuoteResult getQuotes(Security s, LocalDate dateStart, List<Exception> errors)
    {
        List<String> lines = getContentOfSecurityPage(s, true, errors);
        if (lines == null)
        {
            // return empty result
            return new CombinedQuoteResult();
        }
        LatestSecurityPrice latest = null;
        // now get the quotes
        CombinedQuoteResult result = new CombinedQuoteResult();
        Pattern pLine = Pattern.compile("\\$\\.extend\\(true,\\ss\\.USFdata\\.(\\w+).*"); //$NON-NLS-1$
        Pattern pSingleQuote = Pattern.compile("\\[(\\d+),([0-9.]+)\\]"); //$NON-NLS-1$
        for (String line : lines)
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
                            long lastPrice = getPrice(ojIntraday, "lastprice"); //$NON-NLS-1$
                            LocalDate lastTimeStamp = getDate(ojIntraday, "lastTimestamp"); //$NON-NLS-1$
                            // if at least time and price are there,
                            // construct a latest quote
                            if ((lastTimeStamp != null) && (lastPrice != -1))
                            {
                                LatestSecurityPrice price = new LatestSecurityPrice(lastTimeStamp, lastPrice);
                                // check if latest security price should be set
                                if ((latest == null) || (latest.compareTo(price) <= 0))
                                {
                                    // set all known properties
                                    price.setLow(getPrice(ojIntraday, "low"));//$NON-NLS-1$
                                    price.setHigh(getPrice(ojIntraday, "high"));//$NON-NLS-1$
                                    price.setPreviousClose(getPrice(ojIntraday, "yesterdayPrice"));//$NON-NLS-1$
                                    // add latest quote to returned result
                                    latest = price;
                                }
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
                        LocalDate date = LocalDateTime
                                        .ofInstant(Instant.ofEpochMilli(lTime), TimeZone.getDefault().toZoneId())
                                        .toLocalDate();
                        // check if start date is given and if so, if
                        // current
                        // date is not before it
                        if ((dateStart == null) || (date.compareTo(dateStart) >= 0))
                        {
                            LatestSecurityPrice price = new LatestSecurityPrice(date, getPrice(dValue));
                            if ((latest == null) || (latest.compareTo(price) <= 0))
                            {
                                // add latest quote to returned result
                                latest = price;
                            }
                            result.prices.add(price);
                        }
                    }
                }
            }
        }
        // now try to refine the data a bit...
        if (!result.prices.isEmpty())
        {
            // first sort prices to make sure they are in order
            Collections.sort(result.prices);
            // now try to find the previous close etc.
            LocalDate lastDay = null;
            long prevClose = -1;
            long nextClose = -1;
            for (LatestSecurityPrice price : result.prices)
            {
                // first one, just remember values
                if (lastDay == null)
                {
                    lastDay = price.getDate();
                    nextClose = price.getValue();
                    continue;
                }
                // check if this is a new day
                if (lastDay.isBefore(price.getDate()))
                {
                    // switch close prices
                    prevClose = nextClose;
                }
                // remember the price as the next one
                nextClose = price.getValue();
                // check if previous close is missing and set it then
                if ((price.getPreviousClose() <= 0) && (prevClose > 0))
                {
                    price.setPreviousClose(prevClose);
                }
            }
            // set the latest price
            result.latest = latest;
        }
        return result;
    }

    /**
     * Open a stream to the given {@link URL}.
     *
     * @param url
     *            {@link URL}
     * @param referrer
     *            referrer URL (can be null)
     * @return {@link InputStream}
     * @throws IOException
     */
    private static InputStream openUrlStream(URL url, String referrer) throws IOException
    {
        URLConnection c = url.openConnection();
        // use a different user agent
        c.setRequestProperty("User-Agent", OnlineHelper.getUserAgent()); //$NON-NLS-1$
        if (referrer != null)
        {
            c.setRequestProperty("Referer", referrer); //$NON-NLS-1$
        }
        c.connect();
        return c.getInputStream();
    }

    /**
     * Reads the given {@link InputStream} to a list of lines.
     *
     * @param is
     *            {@link InputStream}
     * @return list of lines
     * @throws IOException
     */
    private static List<String> readToLines(InputStream is) throws IOException
    {
        ArrayList<String> lines = new ArrayList<String>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = br.readLine()) != null)
            {
                lines.add(line);
            }
        }
        return lines;
    }

    @Override
    public List<Exchange> getExchanges(Security subject, List<Exception> errors)
    {
        // try to get list of exchanges from security page
        List<String> lines = getContentOfSecurityPage(subject, false, errors);
        if (lines != null)
        {
            List<Exchange> exchanges = new ArrayList<Exchange>();
            // <a onclick="USFchangeSnapqoute('frankfurt');"
            // href="http://fonds.finanztreff.de/fonds_einzelkurs_uebersicht.htn?i=2401356">Frankfurt</a>
            Pattern pLine = Pattern.compile("USFchangeSnapqoute[^ ]+ href=\"([^\"]+)\">([^<]+)"); //$NON-NLS-1$
            for (String line : lines)
            {
                Matcher m = pLine.matcher(line);
                while (m.find())
                {
                    String url = m.group(1);
                    String name = m.group(2);
                    Exchange exchange = new Exchange(url, name);
                    exchanges.add(exchange);
                }
            }
            if (!exchanges.isEmpty()) { return exchanges; }
        }
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
        LatestSecurityPrice latest = result.latest;
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
    public boolean updateLatestQuotes(Security security, List<Exception> errors)
    {
        // get standard quotes
        CombinedQuoteResult result = getQuotes(security, null, errors);
        // get latest price if possible
        LatestSecurityPrice latest = result.latest;
        if (latest != null)
        {
            // set it
            return security.setLatest(latest);
        }
        return false;
    }
}
