# InvRestore
Fabric mod that saves snapshots of the player's inventory when they die, join, leave, or change dimensions. Allowing moderators to list, view, and restore them later.

## Commands
* `/ir` alias of `/invrestore`
* `/invrestore list <player name> [<event type>]`
  * Shows a list of the most recent snapshots of a player
  * `[<event type>]` is optionally one of `death`, `join`, `disconnect`, or `level_change`
* `/invrestore timezone <timezone>`
  * Configures the timezone to use for you personally when formatting times in chat

## Config
Some functionality can be configured in a `config/invrestore/config.json` file
* `query_results` Controls how to format the results in chat
  * `max_results` (default: `5`) Maximum number of snapshot lines shown in chat
  * `default_zone` (default: `UTC`) Default zone when a player hasn't set one
  * `full_time_format` (default: `yyyy-MM-dd HH:mm:ss (z)`) Format of timestamps when hovered
* `store_limits` Controls when older snapshots will be discarded
  * `max_per_player` (default: `50`) Maximum snapshots per player that will be stored
  * `max_total` (default: `10000`) Maximum snapshots that will be stored
