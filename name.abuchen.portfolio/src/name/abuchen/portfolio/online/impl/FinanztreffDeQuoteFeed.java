package name.abuchen.portfolio.online.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * ID of the provider.
     */
    public static final String ID = "FINANZTREFF_DE"; //$NON-NLS-1$
    private static final String QUERY_URL = "http://www.finanztreff.de/ajax/get_search.htn?suchbegriff={key}"; //$NON-NLS-1$
    private static final String QUERY_QUOTES_URL = "http://www.finanztreff.de/kurse_einzelkurs_portrait.htn"; //$NON-NLS-1$
    private static final String REFERRER_URL = "http://www.finanztreff.de"; //$NON-NLS-1$
    /**
     * Ajax flag for quotes.
     */
    private static final String AJAX_QUOTE = "2"; //$NON-NLS-1$

    /**
     * Collects all query result URLs from the given {@link InputStream}.
     *
     * @param is
     *            {@link InputStream}
     * @return list of query result URLs
     * @throws IOException
     */
    private static List<String> collectQueryResultUrls(InputStream is) throws IOException
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
                        // check url
                        String url = m.group(1);
                        // filter news
                        if (!url.contains("/news/")) //$NON-NLS-1$
                        {
                            // remember url
                            results.add(url);
                        }
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
        //FIX
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
     * Gets the content of the web page for the given {@link SecurityPage}.
     * 
     * @param p
     *            {@link SecurityPage}
     * @param url
     *            url
     * @param referrer
     *            referrer
     * @param errors
     *            errors list
     * @throws MalformedURLException
     */
    private static void getContentOfSecurityPage(SecurityPage p, String url, String referrer, List<Exception> errors)
                    throws MalformedURLException
    {
        try (InputStream is = openUrlStream(new URL(url), referrer))
        {
            // cache data
            p.setContentLines(readToLines(is));
        }
        catch (MalformedURLException ex)
        {
            throw ex;
        }
        catch (IOException ex)
        {
            errors.add(ex);
        }

        // try to match currency of security?
        if ((p.getContentLines() != null))
        {
            // retrieve the actual quotes page using an XHR request
            try (InputStream is = openUrlStreamUsingXmlHttpRequest(new URL(QUERY_QUOTES_URL), p.getUrl(), p, AJAX_QUOTE))
            {
                // cache data
                p.setContentLines(readToLines(is));
                p.setReferrer(p.getUrl());
                p.setUrl(QUERY_QUOTES_URL);
            }
            catch (IOException ex)
            {
                errors.add(ex);
            }
        }
    }
    
    /**
     * Gets the content of the web page for the given {@link Security}.
     *
     * @param s
     *            {@link Security}
     * @param errors
     *            errors list
     * @return {@link SecurityPage} on success, else null
     */
    private static SecurityPage getContentOfSecurityPage(Security s, List<Exception> errors)
    {
        // get content for quotes then check for exchange
            // check if ticker symbol is there
            String exchangeUrl = s.getTickerSymbol();
            if ((exchangeUrl != null) && !exchangeUrl.isEmpty())
            {
                try
                {
                    SecurityPage p = new SecurityPage(exchangeUrl, null);
                    getContentOfSecurityPage(p, exchangeUrl, REFERRER_URL, errors);
                    return p;
                }
                catch (MalformedURLException ex)
                {
                    // ignore if this is not an actual URL
                }
            }
        // retrieve data using the security search key
        String key = getKey(s);
        SecurityPage p = null;
        if (key != null)
        {
            // execute query
            String sQueryUrl = QUERY_URL.replace("{key}", key); //$NON-NLS-1$
            try (InputStream is = openUrlStream(new URL(sQueryUrl), REFERRER_URL))
            {
                List<String> results = collectQueryResultUrls(is);
                // get first result
                if (!results.isEmpty())
                {
                    p = new SecurityPage(results.get(0), sQueryUrl);
                }
            }
            catch (IOException ex)
            {
                errors.add(ex);
            }
        }
        if (p != null)
        {
            try
            {
                getContentOfSecurityPage(p, p.getUrl(), p.getReferrer(), errors);
            }
            catch (IOException ex)
            {
                errors.add(ex);
            }

            // try to match currency of security?
            if (p.getContentLines() != null)
            {
                // check if currency matches
                String securityCurrency = s.getCurrencyCode();
                String currency = getCurrency(p.getContentLines());
                if ((currency != null) && !currency.equals(securityCurrency))
                {
                    // collect all available pages
                    List<String> lQuoteUrls = collectQuotePages(p.getContentLines());
                    // try to get the correct currency page
                    if (!lQuoteUrls.isEmpty())
                    {
                        for (String sQuoteUrl : lQuoteUrls)
                        {
                            List<String> lines2 = null;
                            try (InputStream is = openUrlStreamUsingXmlHttpRequest(new URL(sQuoteUrl), p.getUrl(), p, AJAX_QUOTE))
                            {
                                // cache data
                                lines2 = readToLines(is);
                                String currency2 = getCurrency(lines2);
                                // if currency matches, use that page data
                                if (securityCurrency.equals(currency2))
                                {
                                    p.setContentLines(lines2);
                                    p.setReferrer(p.getUrl());
                                    p.setUrl(sQuoteUrl);
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
            return p;
        }
        return null;
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
            if (m.find())
            {
                return m.group(1);
            }
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
        if (l != null)
        {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(l.longValue()), TimeZone.getDefault().toZoneId())
                            .toLocalDate();
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
            if (!Double.isNaN(v))
            {
                return Math.round(v * Values.Quote.factor());
            }
        }
        return -1;
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
        SecurityPage p = getContentOfSecurityPage(s, errors);
        if ((p == null) || (p.getContentLines() == null))
        {
            // return empty result
            return new CombinedQuoteResult();
        }
        LatestSecurityPrice latest = null;
        // now get the quotes
        CombinedQuoteResult result = new CombinedQuoteResult();
        Pattern pLine = Pattern.compile("\\$\\.extend\\(true,\\ss\\.USFdata\\.(\\w+).*"); //$NON-NLS-1$
        Pattern pSingleQuote = Pattern.compile("\\[(\\d+),([0-9.]+)\\]"); //$NON-NLS-1$
        for (String line : p.getContentLines())
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
                        if ((iJsonStart >= 0) && (iJsonEnd > 0))
                        {
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
                                    // check if latest security price should be
                                    // set
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
     * Open a stream to the given {@link URL} using a XHR.
     *
     * @param url
     *            {@link URL}
     * @param referrer
     *            referrer URL (can be null)
     * @param p
     *            related {@link SecurityPage}
     * @param ajax
     * @return {@link InputStream}
     * @throws IOException
     */
    private static InputStream openUrlStreamUsingXmlHttpRequest(URL url, String referrer, SecurityPage p, String ajax) throws IOException
    {
        // encode data fields
        HashMap<String, String> params = new HashMap<String, String>();
        if (ajax != null)
        {
            params.put("ajax", ajax); //$NON-NLS-1$
        }
        if (p.getArbitrageId() != null)
        {
            params.put("arbitrageId", p.getArbitrageId()); //$NON-NLS-1$
        }
        if (p.getPushFrameId() != null)
        {
            params.put("pushFrameId", p.getPushFrameId()); //$NON-NLS-1$
        }
        if (p.getExchange() != null)
        {
            params.put("exchange", p.getExchange()); //$NON-NLS-1$
        }
        StringBuilder postData = new StringBuilder();
        for (Map.Entry<String, String> param : params.entrySet())
        {
            if (postData.length() != 0)
            {
                postData.append('&');
            }
            postData.append(URLEncoder.encode(param.getKey(), "UTF-8")); //$NON-NLS-1$
            postData.append('=');
            postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8")); //$NON-NLS-1$
        }
        byte[] postDataBytes = postData.toString().getBytes("UTF-8"); //$NON-NLS-1$

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST"); //$NON-NLS-1$

        // use a different user agent
        conn.setRequestProperty("User-Agent", OnlineHelper.getUserAgent()); //$NON-NLS-1$
        if (referrer != null)
        {
            conn.setRequestProperty("Referer", referrer); //$NON-NLS-1$
        }
        conn.setRequestProperty("Content-type", "application/x-www-form-urlencoded; charset=UTF-8"); //$NON-NLS-1$//$NON-NLS-2$
        conn.setRequestProperty("Accept", "*/*"); //$NON-NLS-1$//$NON-NLS-2$
        conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length)); //$NON-NLS-1$
        conn.setDoOutput(true);
        conn.getOutputStream().write(postDataBytes);
        return conn.getInputStream();
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

    /**
     * Checks, if the given {@link String} is null or an empty string (only
     * containing whitespace).
     * 
     * @param s
     *            {@link String}
     * @return true on sucess, else false
     */
    private static boolean stringIsNullOrEmpty(String s)
    {
        return (s == null) || s.trim().isEmpty();
    }

    /**
     * Gets the search key for the given {@link Security}.
     * 
     * @param s
     *            {@link Security}
     * @return search key on success, else null
     */
    private static String getKey(Security s)
    {
        // retrieve data using the ISIN?
        String key = s.getIsin();
        if (stringIsNullOrEmpty(key))
        {
            // then try WKN
            key = s.getWkn();
            if (stringIsNullOrEmpty(key))
            {
                // then ticker symbol
                key = s.getTickerSymbol();
            }
        }
        if (!stringIsNullOrEmpty(key))
        { 
            return key; 
        }
        return null;
    }
    
    @Override
    public List<Exchange> getExchanges(Security subject, List<Exception> errors)
    {
        // retrieve data using the security search key
        String key = getKey(subject);
        if (key != null)
        {
            // execute query to find actual security page
            String sQueryUrl = QUERY_URL.replace("{key}", key); //$NON-NLS-1$
            try (InputStream is = openUrlStream(new URL(sQueryUrl), REFERRER_URL))
            {
                List<String> results = collectQueryResultUrls(is);
                // get first result
                if (!results.isEmpty())
                {
                    List<Exchange> exchanges = new ArrayList<Exchange>();
                    String urlSecurityPage = results.get(0);
                    // read the security page
                    try (InputStream is2 = openUrlStream(new URL(urlSecurityPage), sQueryUrl))
                    {
                        // and process the data
                        List<String> lines = readToLines(is2);
                        //@formatter:off
                        // <a onclick="window.location.href='http://www.finanztreff.de/aktien/kurse/DE0008404005-Allianz-SE-Namensaktie-vinkuliert/#113397'; window.location.reload();"
                        // href="http://www.finanztreff.de/aktien/kurse/DE0008404005-Allianz-SE-Namensaktie-vinkuliert/#113397"
                        // title="ALLIANZ SE VINK.NAMENS-AKTIEN O.N. an Börsenplatz: Xetra" class="selected">Xetra</a>
                        //@formatter:on
                        Pattern pLine = Pattern.compile(
                                        "location.href[^>]+href=[^>]+(http.+[^#]+#[0-9]+).+title=.+rsenplatz[^>]+>([^<]+)<"); //$NON-NLS-1$
                        for (String line : lines)
                        {
                            Matcher m = pLine.matcher(line);
                            while (m.find())
                            {
                                String url = m.group(1);
                                String name = m.group(2);
                                exchanges.add(new Exchange(url, name));
                            }
                        }
                    }
                    // check if there are no other exchanges than just the current one
                    if (exchanges.isEmpty())
                    {
                        exchanges.add(new Exchange(urlSecurityPage, urlSecurityPage));
                    }
                    return exchanges;
                }
            }
            catch (IOException ex)
            {
                errors.add(ex);
            }
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
     * A page for a security.
     */
    private static class SecurityPage
    {
        /**
         * Content lines.
         */
        private List<String> contentLines;
        /**
         * Referrer of the query to get to the page.
         */
        private String referrer;

        /**
         * Url of the security's page.
         */
        private String url;

        /**
         * The exchange of this security's page.
         */
        private String exchange;
        
        private String arbitrageId;
        
        private String pushFrameId;
        
        /**
         * Constructs an instance.
         *
         * @param url
         *            url of the security's page
         * @param referrer
         *            referrer of the query to get to the page
         */
        public SecurityPage(String url, String referrer)
        {
            this.setReferrer(referrer);
            setUrl(url);
        }

        public String getArbitrageId()
        {
            return arbitrageId;
        }
        
        public List<String> getContentLines()
        {
            return contentLines;
        }

        public String getExchange()
        {
            return exchange;
        }

        public String getPushFrameId()
        {
            return pushFrameId;
        }

        public String getReferrer()
        {
            return referrer;
        }

        public String getUrl()
        {
            return url;
        }

        public void setContentLines(List<String> contentLines)
        {
            this.contentLines = contentLines;
            // parse contents
            // &arbitrageId=5414514
            Pattern pArbitrageId = Pattern.compile(".*&arbitrageId=([0-9]+)&.*"); //$NON-NLS-1$
            // &pushFrameId=PFIG3wsqMgRaiipf547VjDtKw&
            Pattern pPushFrameId = Pattern.compile(".*&pushFrameId=([^&]+)&.*"); //$NON-NLS-1$
            // <!-- instrumentId 113397 -->
            Pattern pCurrentExchange = Pattern.compile(".*instrumentId ([0-9]+).*"); //$NON-NLS-1$
            for (String line : contentLines)
            {
                Matcher m = pArbitrageId.matcher(line);
                if (m.matches())
                {
                    this.arbitrageId = m.group(1);
                }
                m = pPushFrameId.matcher(line);
                if (m.matches())
                {
                    this.pushFrameId = m.group(1);
                }
                // try to get current exchange?
                if (this.exchange == null)
                {
                    m = pCurrentExchange.matcher(line);
                    if (m.matches())
                    {
                        this.exchange = m.group(1);
                    }
                }
            }
        }

        public void setExchange(String exchange)
        {
            this.exchange = exchange;
        }
        
        public void setReferrer(String referrer)
        {
            this.referrer = referrer;
        }

        /**
         * Sets the url.
         * 
         * @param url
         *            url of the security's page
         */
        public void setUrl(String url)
        {
            this.url = url;
            // try to find exchange
            int i = url.lastIndexOf('#');
            if (i > 0)
            {
                this.setExchange(url.substring(i + 1));
            }
            else
            {
                this.setExchange(null);
            }
        }

    }
}
