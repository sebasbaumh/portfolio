package name.abuchen.portfolio.online.impl;

import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.model.Exchange;
import name.abuchen.portfolio.model.LatestSecurityPrice;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;
import name.abuchen.portfolio.online.QuoteFeed;
import name.abuchen.portfolio.util.WebAccess;

public final class KrakenQuoteFeed implements QuoteFeed
{
    public static final String ID = "KRAKEN"; //$NON-NLS-1$

    private static final long SECONDS_PER_DAY = 24L * 60 * 60;

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public String getName()
    {
        return "Kraken Cryptocurrency Exchange"; //$NON-NLS-1$
    }

    @Override
    public boolean updateLatestQuotes(Security security, List<Exception> errors)
    {
        if (security.getTickerSymbol() == null)
        {
            errors.add(new IOException(MessageFormat.format(Messages.MsgMissingTickerSymbol, security.getName())));
            return false;
        }

        List<LatestSecurityPrice> prices = getHistoricalQuotes(security, LocalDate.now(), errors);

        if (prices.isEmpty())
        {
            return false;
        }
        else
        {
            LatestSecurityPrice price = prices.get(prices.size() - 1);
            if (price.getValue() != 0)
                security.setLatest(price);

            return true;
        }
    }

    @Override
    public boolean updateHistoricalQuotes(Security security, List<Exception> errors)
    {
        if (security.getTickerSymbol() == null)
        {
            errors.add(new IOException(MessageFormat.format(Messages.MsgMissingTickerSymbol, security.getName())));
            return false;
        }

        LocalDate quoteStartDate = LocalDate.MIN;

        if (!security.getPrices().isEmpty())
            quoteStartDate = security.getPrices().get(security.getPrices().size() - 1).getDate();

        List<SecurityPrice> prices = getHistoricalQuotes(SecurityPrice.class, security, quoteStartDate, errors);

        boolean isUpdated = false;
        for (SecurityPrice p : prices)
        {
            if (p.getDate().isBefore(LocalDate.now()))
            {
                boolean isAdded = security.addPrice(p);
                isUpdated = isUpdated || isAdded;
            }
        }
        return isUpdated;
    }

    @Override
    public List<LatestSecurityPrice> getHistoricalQuotes(Security security, LocalDate start, List<Exception> errors)
    {
        return getHistoricalQuotes(LatestSecurityPrice.class, security, start, errors);
    }

    @SuppressWarnings("unchecked")
    public <T extends SecurityPrice> List<T> getHistoricalQuotes(Class<T> klass, Security security, LocalDate start,
                    List<Exception> errors)
    {
        final long tickerStartEpochSeconds = start.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        try
        {
            @SuppressWarnings("nls")
            String html = new WebAccess("api.kraken.com", "/0/public/OHLC")
                            .addParameter("pair", security.getTickerSymbol()) //
                            .addParameter("since", String.valueOf(tickerStartEpochSeconds)) //
                            .addParameter("interval", "1440") //
                            .get();

            JSONObject json = (JSONObject) JSONValue.parse(html);
            JSONArray errorItems = (JSONArray) json.get("error"); //$NON-NLS-1$
            if (!errorItems.isEmpty())
                throw new IOException(this.getName() + " --> " + errorItems.toString()); //$NON-NLS-1$
            JSONObject result = (JSONObject) json.get("result"); //$NON-NLS-1$
            JSONArray ohlcItems = (JSONArray) result.get(security.getTickerSymbol());
            List<T> prices = new ArrayList<>();
            ohlcItems.forEach(e -> {
                JSONArray quoteEntry = (JSONArray) e;
                Long timestamp = Long.parseLong(quoteEntry.get(0).toString());

                try
                {
                    long open = YahooHelper.asPrice(quoteEntry.get(1).toString());
                    long high = YahooHelper.asPrice(quoteEntry.get(2).toString());
                    long low = YahooHelper.asPrice(quoteEntry.get(3).toString());
                    long close = YahooHelper.asPrice(quoteEntry.get(4).toString());
                    int volume = YahooHelper.asNumber(quoteEntry.get(6).toString());

                    T price = klass.getConstructor().newInstance();
                    price.setDate(LocalDate.ofEpochDay(timestamp / SECONDS_PER_DAY));
                    price.setValue(close);

                    if (price instanceof LatestSecurityPrice)
                    {
                        LatestSecurityPrice lsp = (LatestSecurityPrice) price;
                        lsp.setHigh(high);
                        lsp.setLow(low);
                        lsp.setVolume(volume);
                        lsp.setPreviousClose(open);
                    }

                    prices.add(price);

                }
                catch (ReflectiveOperationException | ParseException | IllegalArgumentException | SecurityException ex)
                {
                    errors.add(ex);
                }
            });
            return prices;

        }
        catch (IOException e)
        {
            errors.add(e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<LatestSecurityPrice> getHistoricalQuotes(String response, List<Exception> errors)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Exchange> getExchanges(Security subject, List<Exception> errors)
    {
        return Collections.emptyList();
    }

}
