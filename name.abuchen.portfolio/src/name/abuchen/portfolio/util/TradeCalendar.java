package name.abuchen.portfolio.util;

import java.text.Collator;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TradeCalendar implements Comparable<TradeCalendar>
{
    private final Set<DayOfWeek> weekend;

    private final String code;
    private final String description;

    /**
     * Indicates whether this calendar can be selected in the user interface.
     */
    private final boolean isSelectable;

    private final List<HolidayType> holidayTypes = new ArrayList<>();
    private final Map<Integer, Map<LocalDate, Holiday>> cache = new HashMap<Integer, Map<LocalDate, Holiday>>()
    {
        private static final long serialVersionUID = 1L;

        @Override
        public Map<LocalDate, Holiday> get(Object key)
        {
            return super.computeIfAbsent((Integer) key, year -> holidayTypes.stream().map(type -> type.getHoliday(year))
                            .filter(Objects::nonNull).collect(Collectors.toMap(Holiday::getDate, t -> t, (r, l) -> r)));
        }

    };

    /* package */ TradeCalendar(String code, String description, Set<DayOfWeek> weekend, boolean isSelectable)
    {
        this.isSelectable = isSelectable;
        this.code = Objects.requireNonNull(code);
        this.description = Objects.requireNonNull(description);
        this.weekend = Objects.requireNonNull(weekend);
    }

    /* package */ TradeCalendar(String code, String description, Set<DayOfWeek> weekend)
    {
        this(code, description, weekend, true);
    }

    /* package */ void add(HolidayType type)
    {
        this.holidayTypes.add(type);
    }

    public String getCode()
    {
        return code;
    }

    public String getDescription()
    {
        return description;
    }

    public boolean isSelectable()
    {
        return isSelectable;
    }

    @Override
    public String toString()
    {
        return getDescription();
    }

    /**
     * Tests whether {@code date} is a weekend day in this calendar.
     */
    public boolean isWeekend(LocalDate date)
    {
        return weekend.contains(date.getDayOfWeek());
    }

    /**
     * Tests whether {@code date} is a non-trading day, i.e. a holiday or
     * weekend day.
     */
    public boolean isHoliday(LocalDate date)
    {
        if (weekend.contains(date.getDayOfWeek()))
            return true;

        return cache.get(date.getYear()).containsKey(date);
    }

    public Holiday getHoliday(LocalDate date)
    {
        return cache.get(date.getYear()).get(date);
    }

    /**
     * @return {@code date}, if date is a trading day. Otherwise the earliest
     *         date after {@code date} that is a trading day.
     */
    public LocalDate getNextNonHoliday(LocalDate date)
    {
        while (this.isHoliday(date))
            date = date.plusDays(1);
        return date;
    }

    public Collection<Holiday> getHolidays(int year)
    {
        return cache.get(year).values();
    }

    @Override
    public int compareTo(TradeCalendar other)
    {
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.SECONDARY);
        return collator.compare(getDescription(), other.getDescription());
    }

    @Override
    public int hashCode()
    {
        return code.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TradeCalendar other = (TradeCalendar) obj;
        return code.equals(other.code);
    }
}
