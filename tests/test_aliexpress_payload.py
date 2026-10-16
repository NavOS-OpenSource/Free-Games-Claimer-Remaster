"""AliExpress coin/check-in payload parsing.

The wallet balance arrives as a [{"name": …, "value": …}] list, the check-in
calendar as a plain nested object, the parser has to read both, or the streak
and tomorrow's reward silently disappear (v1.5 bug).
"""

import json
from pathlib import Path

import pytest

from src.stores.aliexpress import (
    AliExpressClaimer,
    _as_int,
    _field_by_leaf,
    _flatten_payload,
)

# Shape of a real mtop.aliexpress.coin.execute response (values from a live run).
COIN_EXECUTE = {
    "success": True,
    "data": [
        {"name": "userCoinsNum", "value": 985},
        {"name": "defaultCoinsNum", "value": 100},
        {"name": "valueMoneyFormat", "value": json.dumps({"structure": {"cent": 3843, "currencyCode": "PLN"}})},
    ],
}

# Shape of a check-in response: nested, no name/value pairs at all.
SIGN_LIST = {
    "success": True,
    "data": {
        "continuousDays": 5,
        "signInfo": {"todayCoins": 50, "tomorrowCoins": 72},
        "dayList": [{"day": "2026-07-26", "coins": 50, "today": True}],
    },
}


def _sign_node(distance, seq, signed, coins=50):
    """One day of the real check-in calendar (shape taken from a live capture)."""
    return {
        "calendarDayDistance": distance,
        "signResultList": [{
            "sequenceNumber": seq,
            "signSuccess": signed,
            "prizeInfoList": [{"prizeType": "coins", "prizeAmount": coins}],
        }],
    }


def _calendar(*nodes, inner=None):
    """Full mtop envelope, as captured: {api, data: {data: {...}, success}, ret, v}."""
    payload = {"signQuerySequenceNodeList": [{"dailySignNodeList": list(nodes)}]} if inner is None else inner
    return {
        "api": "mtop.aliexpress.coin.channel.sign.list",
        "data": {"data": payload, "success": True},
        "ret": ["SUCCESS::接口调用成功"],
        "v": "1.0",
    }


# The calendar as AliExpress actually returns it: today is distance 0, tomorrow 1.
REAL_CALENDAR = _calendar(
    _sign_node(-2, 13, True), _sign_node(-1, 14, True),
    _sign_node(0, 15, False), _sign_node(1, 16, False), _sign_node(2, 17, False),
)


def _claimer(*payloads):
    claimer = AliExpressClaimer()
    claimer._coin_payloads = [
        {"api": api, "url": api, "fields": _flatten_payload(raw), "body": json.dumps(raw)}
        for api, raw in payloads
    ]
    return claimer


class TestFlattenPayload:
    def test_reads_name_value_pairs(self):
        fields = _flatten_payload(COIN_EXECUTE)
        assert _as_int(_field_by_leaf(fields, "userCoinsNum")) == 985
        assert _as_int(_field_by_leaf(fields, "defaultCoinsNum")) == 100

    def test_name_value_entries_are_not_indexed(self):
        # "data[0].userCoinsNum" would break every lookup by field name.
        assert "data.userCoinsNum" in _flatten_payload(COIN_EXECUTE)

    def test_parses_json_encoded_strings(self):
        fields = _flatten_payload(COIN_EXECUTE)
        assert _as_int(_field_by_leaf(fields, "cent")) == 3843

    def test_reads_plain_nested_objects(self):
        fields = _flatten_payload(SIGN_LIST)
        assert fields["data.continuousDays"] == 5
        assert fields["data.signInfo.tomorrowCoins"] == 72
        assert fields["data.dayList[0].coins"] == 50

    def test_survives_deep_nesting(self):
        deep = {"a": {"b": {"c": {"d": {"e": {"f": {"g": {"h": 1}}}}}}}}
        assert isinstance(_flatten_payload(deep), dict)

    def test_handles_empty_and_scalar_input(self):
        assert _flatten_payload({}) == {}
        assert _flatten_payload([]) == {}


class TestAsInt:
    def test_accepts_numbers_and_numeric_text(self):
        assert _as_int(5) == 5
        assert _as_int("5") == 5
        assert _as_int("5 days") == 5
        assert _as_int(5.9) == 5

    def test_rejects_bools_and_junk(self):
        assert _as_int(True) is None
        assert _as_int(None) is None
        assert _as_int("no digits here") is None


class TestCheckinCalendar:
    """The calendar is the authoritative source; the live page has no named streak field."""

    def test_reads_streak_and_tomorrow_from_the_real_shape(self):
        info = _claimer(("mtop.aliexpress.coin.channel.sign.list", REAL_CALENDAR))._extract_checkin_info_from_api()
        assert info == {"streak": 15, "tomorrow": 50}

    def test_tomorrow_is_the_prize_not_the_day_number(self):
        # The day counter (16) sits right next to the prize; reporting it would be wrong.
        info = _claimer(("mtop.aliexpress.coin.channel.sign.list", REAL_CALENDAR))._extract_checkin_info_from_api()
        assert info["tomorrow"] == 50

    def test_after_a_missed_day_the_streak_counts_from_it(self):
        # The counter kept going over the gap, so the signed days since the gap are the streak: none yet today.
        broken = _calendar(_sign_node(-2, 13, True), _sign_node(-1, 14, False),
                           _sign_node(0, 15, False), _sign_node(1, 16, False))
        info = _claimer(("mtop.aliexpress.coin.channel.sign.list", broken))._extract_checkin_info_from_api()
        assert info["streak"] == 0
        assert info["tomorrow"] == 50

    def test_a_broken_streak_reads_one_after_todays_collect(self):
        # Live, 16.10: two days missed, the page said "1 dniowy ciąg", and the old code showed 49 from a product title.
        before = _calendar(_sign_node(-2, 1, False), _sign_node(-1, 1, False), _sign_node(0, 1, False, 10),
                           _sign_node(1, 2, False, 15))
        after = _calendar(_sign_node(-2, 1, False), _sign_node(-1, 1, False), _sign_node(0, 1, True, 10),
                          _sign_node(1, 2, False, 15))
        claimer = _claimer(("mtop.aliexpress.coin.channel.sign.list", before),
                           ("mtop.aliexpress.coin.channel.sign.list", after))
        assert claimer._extract_checkin_info_from_api() == {"streak": 1, "tomorrow": 15}

    def test_a_long_streak_without_a_gap_is_the_counter(self):
        running = _calendar(_sign_node(-2, 47, True), _sign_node(-1, 48, True), _sign_node(0, 49, True))
        info = _claimer(("mtop.aliexpress.coin.channel.sign.list", running))._extract_checkin_info_from_api()
        assert info["streak"] == 49

    def test_the_page_is_never_read_for_the_streak(self):
        # Polish pages say "dniowy ciąg", and "Seria" in product titles was taken for the streak.
        source = (Path(__file__).resolve().parent.parent / "src" / "stores" / "aliexpress.py").read_text(encoding="utf-8")
        assert "_read_checkin_info_dom" not in source and "day streak|days? in a row" not in source

    def test_calendar_without_tomorrow_still_reports_the_streak(self):
        today_only = _calendar(_sign_node(-1, 14, True), _sign_node(0, 15, False))
        info = _claimer(("mtop.aliexpress.coin.channel.sign.list", today_only))._extract_checkin_info_from_api()
        assert info["streak"] == 15
        assert info["tomorrow"] is None

    def test_malformed_calendar_is_ignored(self):
        for broken in (_calendar(inner={"signQuerySequenceNodeList": "nope"}), _calendar(inner={})):
            info = _claimer(("mtop.aliexpress.coin.channel.sign.list", broken))._extract_checkin_info_from_api()
            assert info == {"streak": None, "tomorrow": None}


class TestCheckinInfo:
    def test_reads_streak_and_tomorrow_from_calendar(self):
        info = _claimer(("mtop.aliexpress.coin.channel.sign.list", SIGN_LIST))._extract_checkin_info_from_api()
        assert info == {"streak": 5, "tomorrow": 72}

    def test_prefers_the_response_from_the_collect_itself(self):
        fresh = {"success": True, "data": {"signDays": 6, "nextDayCoins": 80}}
        info = _claimer(
            ("mtop.aliexpress.coin.channel.sign.list", SIGN_LIST),
            ("mtop.aliexpress.coin.channel.sign.execute", fresh),
        )._extract_checkin_info_from_api()
        assert info == {"streak": 6, "tomorrow": 80}

    def test_no_false_positives_without_checkin_fields(self):
        info = _claimer(("mtop.aliexpress.coin.execute", COIN_EXECUTE))._extract_checkin_info_from_api()
        assert info == {"streak": None, "tomorrow": None}

    def test_nothing_captured(self):
        assert _claimer()._extract_checkin_info_from_api() == {"streak": None, "tomorrow": None}

    @pytest.mark.parametrize("field", ["nextDayIndex", "nextDayStatus", "nextSignDay", "tomorrowDate"])
    def test_next_day_fields_that_are_not_coins_are_ignored(self, field):
        # Showing a day number as "tomorrow 12 🪙" is worse than showing nothing.
        payload = {"success": True, "data": {field: 12}}
        info = _claimer(("mtop.aliexpress.coin.channel.sign.list", payload))._extract_checkin_info_from_api()
        assert info["tomorrow"] is None

    @pytest.mark.parametrize("field", ["tomorrowCoins", "nextDayCoins", "nextDayRewardAmount", "coinsTomorrow"])
    def test_next_day_coin_fields_are_read(self, field):
        payload = {"success": True, "data": {field: 72}}
        info = _claimer(("mtop.aliexpress.coin.channel.sign.list", payload))._extract_checkin_info_from_api()
        assert info["tomorrow"] == 72

    @pytest.mark.parametrize("field", ["continuousDays", "consecutiveDays", "signDays", "checkInDays", "streak"])
    def test_streak_fields_are_read(self, field):
        payload = {"success": True, "data": {field: 7}}
        info = _claimer(("mtop.aliexpress.coin.channel.sign.list", payload))._extract_checkin_info_from_api()
        assert info["streak"] == 7

    @pytest.mark.parametrize("field", ["days", "totalDays", "dayIndex"])
    def test_bare_day_counters_are_not_treated_as_a_streak(self, field):
        payload = {"success": True, "data": {field: 7}}
        info = _claimer(("mtop.aliexpress.coin.channel.sign.list", payload))._extract_checkin_info_from_api()
        assert info["streak"] is None

    def test_one_field_cannot_fill_both_slots(self):
        # A key like "nextDaySignCoins" could match both patterns; it may not report twice.
        payload = {"success": True, "data": {"continuousSignDaysNextDayCoins": 9}}
        info = _claimer(("mtop.aliexpress.coin.channel.sign.list", payload))._extract_checkin_info_from_api()
        assert (info["streak"], info["tomorrow"]) != (9, 9)


class TestStatusText:
    def test_every_number_is_labelled(self):
        status = AliExpressClaimer()._format_checkin_status(50, {"streak": 5, "tomorrow": 72}, 985)
        assert status == "claimed 50 🪙, streak 5 days, tomorrow 72 🪙, balance 985 🪙"

    def test_omits_unknown_parts_instead_of_faking_them(self):
        status = AliExpressClaimer()._format_checkin_status(50, {"streak": None, "tomorrow": None}, 985)
        assert status == "claimed 50 🪙, balance 985 🪙"

    def test_singular_day(self):
        status = AliExpressClaimer()._format_checkin_status(10, {"streak": 1, "tomorrow": None}, None)
        assert status == "claimed 10 🪙, streak 1 day"

    def test_large_balance_is_grouped(self):
        status = AliExpressClaimer()._format_checkin_status(50, {}, 1040)
        assert status == "claimed 50 🪙, balance 1,040 🪙"

    def test_no_em_dash_anywhere_in_the_line(self):
        status = AliExpressClaimer()._format_checkin_status(50, {"streak": 5, "tomorrow": 72}, 1040)
        assert "—" not in status
