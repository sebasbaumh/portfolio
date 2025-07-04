package name.abuchen.portfolio.online.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import com.google.common.annotations.VisibleForTesting;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ReadContext;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.PortfolioLog;
import name.abuchen.portfolio.model.LatestSecurityPrice;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;
import name.abuchen.portfolio.model.SecurityProperty;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.online.QuoteFeed;
import name.abuchen.portfolio.online.QuoteFeedData;
import name.abuchen.portfolio.online.QuoteFeedException;
import name.abuchen.portfolio.online.impl.variableurl.Factory;
import name.abuchen.portfolio.online.impl.variableurl.urls.VariableURL;
import name.abuchen.portfolio.util.OnlineHelper;
import name.abuchen.portfolio.util.TextUtil;
import name.abuchen.portfolio.util.WebAccess;

public class GenericJSONQuoteFeed implements QuoteFeed
{
    public static final String ID = "GENERIC-JSON"; //$NON-NLS-1$
    public static final String DATE_PROPERTY_NAME_HISTORIC = "GENERIC-JSON-DATE"; //$NON-NLS-1$
    public static final String DATE_FORMAT_PROPERTY_NAME_HISTORIC = "GENERIC-JSON-DATE-FORMAT"; //$NON-NLS-1$
    public static final String DATE_TIMEZONE_PROPERTY_NAME_HISTORIC = "GENERIC-JSON-DATE-TIMEZONE"; //$NON-NLS-1$
    public static final String CLOSE_PROPERTY_NAME_HISTORIC = "GENERIC-JSON-CLOSE"; //$NON-NLS-1$
    public static final String HIGH_PROPERTY_NAME_HISTORIC = "GENERIC-JSON-HIGH"; //$NON-NLS-1$
    public static final String LOW_PROPERTY_NAME_HISTORIC = "GENERIC-JSON-LOW"; //$NON-NLS-1$
    public static final String FACTOR_PROPERTY_NAME_HISTORIC = "GENERIC-JSON-FACTOR"; //$NON-NLS-1$
    public static final String VOLUME_PROPERTY_NAME_HISTORIC = "GENERIC-JSON-VOLUME"; //$NON-NLS-1$
    public static final String DATE_PROPERTY_NAME_LATEST = "GENERIC-JSON-DATE-LATEST"; //$NON-NLS-1$
    public static final String DATE_FORMAT_PROPERTY_NAME_LATEST = "GENERIC-JSON-DATE-FORMAT-LATEST"; //$NON-NLS-1$
    public static final String DATE_TIMEZONE_PROPERTY_NAME_LATEST = "GENERIC-JSON-DATE-TIMEZONE-LATEST"; //$NON-NLS-1$
    public static final String CLOSE_PROPERTY_NAME_LATEST = "GENERIC-JSON-CLOSE-LATEST"; //$NON-NLS-1$
    public static final String HIGH_PROPERTY_NAME_LATEST = "GENERIC-JSON-HIGH-LATEST"; //$NON-NLS-1$
    public static final String LOW_PROPERTY_NAME_LATEST = "GENERIC-JSON-LOW-LATEST"; //$NON-NLS-1$
    public static final String FACTOR_PROPERTY_NAME_LATEST = "GENERIC-JSON-FACTOR-LATEST"; //$NON-NLS-1$
    public static final String VOLUME_PROPERTY_NAME_LATEST = "GENERIC-JSON-VOLUME-LATEST"; //$NON-NLS-1$

    private final PageCache<String> cache = new PageCache<>();

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public String getName()
    {
        return "JSON"; //$NON-NLS-1$
    }

    @Override
    public Optional<String> getHelpURL()
    {
        return Optional.of("https://help.portfolio-performance.info/de/how-to/kursdaten_laden/#json"); //$NON-NLS-1$
    }

    @Override
    public QuoteFeedData getHistoricalQuotes(Security security, boolean collectRawResponse)
    {
        return getHistoricalQuotes(security, security.getFeedURL(), collectRawResponse, false, false);
    }

    @Override
    public QuoteFeedData previewHistoricalQuotes(Security security)
    {
        return getHistoricalQuotes(security, security.getFeedURL(), true, true, false);
    }

    public String getJson(String url) throws IOException, URISyntaxException
    {
        var userAgent = url.contains("finance.yahoo.com") //$NON-NLS-1$
                        ? OnlineHelper.getYahooFinanceUserAgent()
                        : OnlineHelper.getUserAgent();

        return new WebAccess(url).addUserAgent(userAgent).addHeader("Accept", "application/json").get(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private QuoteFeedData getHistoricalQuotes(Security security, String feedURL, boolean collectRawResponse,
                    boolean isPreview, boolean isLatest)
    {
        Optional<String> dateProperty = security.getPropertyValue(SecurityProperty.Type.FEED,
                        isLatest ? DATE_PROPERTY_NAME_LATEST : DATE_PROPERTY_NAME_HISTORIC);
        Optional<String> dateFormatProperty = security.getPropertyValue(SecurityProperty.Type.FEED,
                        isLatest ? DATE_FORMAT_PROPERTY_NAME_LATEST : DATE_FORMAT_PROPERTY_NAME_HISTORIC);
        Optional<String> dateTimezoneProperty = security.getPropertyValue(SecurityProperty.Type.FEED,
                        isLatest ? DATE_TIMEZONE_PROPERTY_NAME_LATEST : DATE_TIMEZONE_PROPERTY_NAME_HISTORIC);
        Optional<String> closeProperty = security.getPropertyValue(SecurityProperty.Type.FEED,
                        isLatest ? CLOSE_PROPERTY_NAME_LATEST : CLOSE_PROPERTY_NAME_HISTORIC);
        Optional<String> highProperty = security.getPropertyValue(SecurityProperty.Type.FEED,
                        isLatest ? HIGH_PROPERTY_NAME_LATEST : HIGH_PROPERTY_NAME_HISTORIC);
        Optional<String> lowProperty = security.getPropertyValue(SecurityProperty.Type.FEED,
                        isLatest ? LOW_PROPERTY_NAME_LATEST : LOW_PROPERTY_NAME_HISTORIC);
        Optional<String> factorProperty = security.getPropertyValue(SecurityProperty.Type.FEED,
                        isLatest ? FACTOR_PROPERTY_NAME_LATEST : FACTOR_PROPERTY_NAME_HISTORIC);
        Optional<String> volumeProperty = security.getPropertyValue(SecurityProperty.Type.FEED,
                        isLatest ? VOLUME_PROPERTY_NAME_LATEST : VOLUME_PROPERTY_NAME_HISTORIC);

        if (!dateProperty.isPresent() || !closeProperty.isPresent())
        {
            return QuoteFeedData.withError(new IOException(
                            MessageFormat.format(Messages.MsgErrorMissingPathToDateOrClose, security.getName())));
        }

        if (feedURL == null || feedURL.length() == 0)
        {
            return QuoteFeedData.withError(
                            new IOException(MessageFormat.format(Messages.MsgMissingFeedURL, security.getName())));
        }

        VariableURL variableURL = Factory.fromString(feedURL);
        variableURL.setSecurity(security);

        QuoteFeedData data = new QuoteFeedData();

        SortedSet<LatestSecurityPrice> newPricesByDate = new TreeSet<>(new SecurityPrice.ByDate());
        long failedAttempts = 0;
        long maxFailedAttempts = variableURL.getMaxFailedAttempts();

        for (String url : variableURL) // NOSONAR
        {
            String json = cache.lookup(url);

            if (json == null)
            {
                try
                {
                    json = this.getJson(url);
                }
                catch (IOException | URISyntaxException e)
                {
                    data.addError(new IOException(url + '\n' + e.getMessage(), e));
                }

                json = TextUtil.stripJavaScriptCallback(json);

                if (json != null)
                    cache.put(url, json);
            }

            if (collectRawResponse)
                data.addResponse(url, json);

            int sizeBefore = newPricesByDate.size();

            if (json != null)
                newPricesByDate.addAll(parse(url, json, dateProperty.get(), closeProperty.get(), data,
                                dateFormatProperty, dateTimezoneProperty, lowProperty, highProperty, factorProperty,
                                volumeProperty));

            if (newPricesByDate.size() > sizeBefore)
                failedAttempts = 0;
            else if (++failedAttempts > maxFailedAttempts)
                break;

            if (isPreview && newPricesByDate.size() >= 100)
                break;
        }

        data.addAllPrices(newPricesByDate);
        return data;
    }

    @Override
    public Optional<LatestSecurityPrice> getLatestQuote(Security security) throws QuoteFeedException
    {
        // if latestFeed is null, then the policy is 'use same configuration
        // as historic quotes'

        String latestFeed = security.getLatestFeed();

        if (latestFeed == null || latestFeed.isEmpty())
            return QuoteFeed.super.getLatestQuote(security);

        QuoteFeedData data = getHistoricalQuotes(security, security.getLatestFeedURL(), false, false, true);

        if (!data.getErrors().isEmpty())
            PortfolioLog.abbreviated(data.getErrors());

        List<LatestSecurityPrice> prices = data.getLatestPrices();
        if (prices.isEmpty())
            return Optional.empty();

        Collections.sort(prices, new SecurityPrice.ByDate());

        return Optional.of(prices.get(prices.size() - 1));
    }

    protected List<LatestSecurityPrice> parse(String url, String json, String datePath, String closePath,
                    QuoteFeedData data, Optional<String> dateFormat, Optional<String> dateTimezone,
                    Optional<String> lowPath, Optional<String> highPath, Optional<String> factorString,
                    Optional<String> volumePath)
    {
        try
        {
            JsonPath dateP = JsonPath.compile(datePath);
            JsonPath closeP = JsonPath.compile(closePath);

            Configuration configuration = Configuration.defaultConfiguration().addOptions(Option.ALWAYS_RETURN_LIST)
                            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);

            ReadContext ctx = JsonPath.parse(json, configuration);

            List<Object> dates = ctx.read(dateP);
            List<Object> close = ctx.read(closeP);

            Optional<List<Object>> high = Optional.empty();
            Optional<List<Object>> low = Optional.empty();
            Optional<List<Object>> volume = Optional.empty();
            if (highPath.isPresent())
            {
                JsonPath highP = JsonPath.compile(highPath.get());
                high = Optional.of(ctx.read(highP));
            }
            if (lowPath.isPresent())
            {
                JsonPath lowP = JsonPath.compile(lowPath.get());
                low = Optional.of(ctx.read(lowP));
            }
            if (volumePath.isPresent())
            {
                JsonPath volumeP = JsonPath.compile(volumePath.get());
                volume = Optional.of(ctx.read(volumeP));
            }

            if (dates.size() != close.size())
            {
                data.addError(new IOException(MessageFormat.format(
                                Messages.MsgErrorNumberOfDateAndCloseRecordsDoNotMatch, dates.size(), close.size())));
                return Collections.emptyList();
            }

            BigDecimal factor;
            if (factorString.isPresent())
            {
                factor = new BigDecimal(factorString.get());
            }
            else
            {
                factor = BigDecimal.ONE;
            }

            List<LatestSecurityPrice> prices = new ArrayList<>();

            int size = dates.size();

            for (int index = 0; index < size; index++)
            {
                LatestSecurityPrice price = new LatestSecurityPrice();

                // date
                Object object = dates.get(index);
                price.setDate(this.extractDate(object, dateFormat, dateTimezone));

                // close
                object = close.get(index);
                price.setValue(this.extractValue(object, factor));

                if (price.getDate() != null && price.getValue() > 0)
                {
                    if (high.isPresent())
                    {
                        price.setHigh(this.extractValue(high.get().get(index), factor));
                    }
                    else
                    {
                        price.setHigh(LatestSecurityPrice.NOT_AVAILABLE);
                    }
                    if (low.isPresent())
                    {
                        price.setLow(this.extractValue(low.get().get(index), factor));
                    }
                    else
                    {
                        price.setLow(LatestSecurityPrice.NOT_AVAILABLE);
                    }
                    if (volume.isPresent())
                    {
                        price.setVolume(this.extractIntegerValue(volume.get().get(index)));
                    }
                    else
                    {
                        price.setVolume(LatestSecurityPrice.NOT_AVAILABLE);
                    }
                    prices.add(price);
                }
            }

            return prices;
        }
        catch (JsonPathException | ParseException e)
        {
            data.addError(new IOException(url + '\n' + e.getMessage(), e));
            return Collections.emptyList();
        }
    }

    /* testing */ long extractValue(Object object, BigDecimal factor) throws ParseException
    {
        if (object instanceof Number)
            return new BigDecimal(object.toString()).multiply(factor).multiply(Values.Quote.getBigDecimalFactor())
                            .setScale(0, RoundingMode.HALF_UP).longValue();
        else if (object instanceof String s)
            return YahooHelper.asPrice(s, factor);
        return 0;
    }

    /* testing */ long extractIntegerValue(Object object)
    {
        if (object instanceof String s)
            try
            {
                return Long.parseLong(s);
            }
            catch (NumberFormatException e)
            {
                // try again as Double
                return Math.round(Double.parseDouble((String) object));
            }
        if (object instanceof Number num)
            return num.longValue();
        return 0;
    }

    @VisibleForTesting
    /* testing */ LocalDate extractDate(Object object, Optional<String> dateFormat, Optional<String> dateTimezone)
    {
        final ZoneOffset offset = dateTimezone.isPresent()
                        ? ZoneId.of(dateTimezone.get()).getRules().getOffset(Instant.now())
                        : ZoneOffset.UTC;

        if (dateFormat.isPresent())
        {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat.get());
            return parseDate(object.toString(), formatter, offset);
        }

        if (object instanceof String s)
        {

            List<DateTimeFormatter> formatters = Arrays.asList(DateTimeFormatter.ISO_ZONED_DATE_TIME,
                            DateTimeFormatter.ISO_DATE_TIME, DateTimeFormatter.ISO_DATE);

            for (DateTimeFormatter formatter : formatters)
            {
                try
                {
                    return parseDate(s, formatter, offset);
                }
                catch (DateTimeParseException e)
                {
                    // Try next formatter
                }
            }

            return parseDateTimestamp(Long.parseLong(s), offset);
        }
        else if (object instanceof Long l)
            return parseDateTimestamp(l, offset);
        else if (object instanceof Integer i)
            return parseDateTimestamp(Long.valueOf(i), offset);
        else if (object instanceof Double d)
            return parseDateTimestamp(d.longValue(), offset);
        else if (object instanceof LocalDate date)
            return applyOffset(date.atTime(LocalTime.MAX), offset).toLocalDate();
        return null;
    }

    private LocalDate parseDate(String dateString, DateTimeFormatter formatter, ZoneOffset offset)
    {
        TemporalAccessor parsed = formatter.parse(dateString);
        LocalDate date = LocalDate.from(parsed);
        LocalTime time = parsed.isSupported(ChronoField.HOUR_OF_DAY) ? LocalTime.from(parsed) : LocalTime.MAX;

        return applyOffset(LocalDateTime.of(date, time), offset).toLocalDate();
    }

    private static LocalDateTime applyOffset(LocalDateTime localTime, ZoneOffset offset)
    {
        return localTime.atOffset(ZoneOffset.UTC).withOffsetSameInstant(offset).toLocalDateTime();
    }

    private LocalDate parseDateTimestamp(Long object, ZoneOffset offset)
    {
        Long futureEpoch = LocalDateTime.of(2200, 1, 1, 0, 0, 0, 0).toEpochSecond(offset);

        if (object > futureEpoch)
        {
            // if the timestamp represents a date further than year 2200, then
            // it is probably in milliseconds
            // Note: This means that millisecond timestamps before 1970-03-26
            // 00:08:38 can't be parsed by this method
            object = object / 1000;

        }
        else if (object < futureEpoch / (24 * 60 * 60))
        {
            // if the timestamp is smaller than the number of days between 1970
            // and 2200, then it is probably in days
            // Note: This means that second timestamps before 1970-01-01
            // 23:20:06 can't be parsed by this method
            object = object * 24 * 60 * 60;
        }
        // The following does NOT do a time zone conversion. If the API gives
        // epochs as dates, we always convert them to dates with respect to the
        // time zone provided by the user (UTC by default).
        return LocalDateTime.ofEpochSecond(object, 0, offset).toLocalDate();
    }
}
