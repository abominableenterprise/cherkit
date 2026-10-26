extension String {
    /// The string without leading and trailing ASCII whitespace. ASCII-only so Swift and Kotlin trim identically.
    var trimmingASCIIWhitespace: String {
        var scalars = unicodeScalars[...]
        while let first = scalars.first, first.isASCIIWhitespace { scalars = scalars.dropFirst() }
        while let last = scalars.last, last.isASCIIWhitespace { scalars = scalars.dropLast() }
        return String(String.UnicodeScalarView(scalars))
    }
}

private extension Unicode.Scalar {
    var isASCIIWhitespace: Bool { self == " " || (value >= 0x09 && value <= 0x0D) }
}
