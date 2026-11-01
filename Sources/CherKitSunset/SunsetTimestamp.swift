import Foundation

/// Parses the UTC timestamps of the hosted config (`notBefore`), with the same rules as Kotlin's `SunsetTimestamp`.
///
/// Accepted: exactly `YYYY-MM-DDTHH:MM:SSZ`, optionally with `.` and 1 to 9 fraction digits before the `Z`, as written
/// by `date -u +%Y-%m-%dT%H:%M:%SZ`. The date must exist; seconds run 0 to 59. Anything else returns `nil`.
enum SunsetTimestamp {
    /// Seconds since 1970 and the fraction in nanoseconds, or `nil`.
    static func parseEpoch(_ text: String) -> (seconds: Int64, nanoseconds: Int)? {
        let bytes = Array(text.utf8)
        guard bytes.count >= 20, bytes.last == UInt8(ascii: "Z"),
              bytes[4] == UInt8(ascii: "-"), bytes[7] == UInt8(ascii: "-"), bytes[10] == UInt8(ascii: "T"),
              bytes[13] == UInt8(ascii: ":"), bytes[16] == UInt8(ascii: ":"),
              let year = number(bytes, 0, 4), let month = number(bytes, 5, 2), let day = number(bytes, 8, 2),
              let hour = number(bytes, 11, 2), let minute = number(bytes, 14, 2), let second = number(bytes, 17, 2),
              (1...12).contains(month), (1...daysIn(month: month, year: year)).contains(day),
              hour < 24, minute < 60, second < 60
        else { return nil }
        var nanoseconds = 0
        if bytes.count > 20 {
            let digits = bytes.count - 21
            guard bytes[19] == UInt8(ascii: "."), (1...9).contains(digits), let fraction = number(bytes, 20, digits)
            else { return nil }
            nanoseconds = fraction * Int(pow(10, Double(9 - digits)))
        }
        let days = Int64(daysFromCivil(year: year, month: month, day: day))
        return (days * 86_400 + Int64(hour * 3_600 + minute * 60 + second), nanoseconds)
    }

    static func parse(_ text: String) -> Date? {
        guard let (seconds, nanoseconds) = parseEpoch(text) else { return nil }
        return Date(timeIntervalSince1970: Double(seconds) + Double(nanoseconds) / 1_000_000_000)
    }

    private static func number(_ bytes: [UInt8], _ start: Int, _ count: Int) -> Int? {
        var value = 0
        for byte in bytes[start..<(start + count)] {
            guard (UInt8(ascii: "0")...UInt8(ascii: "9")).contains(byte) else { return nil }
            value = value * 10 + Int(byte - UInt8(ascii: "0"))
        }
        return value
    }

    private static func daysIn(month: Int, year: Int) -> Int {
        switch month {
        case 2: return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0) ? 29 : 28
        case 4, 6, 9, 11: return 30
        default: return 31
        }
    }

    /// Days since 1970-01-01 in the proleptic Gregorian calendar (H. Hinnant's `days_from_civil`).
    private static func daysFromCivil(year: Int, month: Int, day: Int) -> Int {
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yearOfEra = y - era * 400
        let dayOfYear = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1
        let dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097 + dayOfEra - 719_468
    }
}
