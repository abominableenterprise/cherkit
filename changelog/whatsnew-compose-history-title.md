whatsnew-compose: added

- `WhatsNewHistoryScreen` takes an optional `showTitle: Boolean = false`. With `true` it shows `strings.historyTitle`
  as a heading (for TalkBack too) at the top of the scrolling list, for screens without a title of their own. Still no
  top bar: navigation stays with the app. The default keeps today's layout.
