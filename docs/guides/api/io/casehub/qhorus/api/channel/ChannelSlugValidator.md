# io.casehub.qhorus.api.channel.ChannelSlugValidator

**Package:** `io.casehub.qhorus.api.channel`

**Kind:** `class`

Validates and utility-parses Qhorus channel name slugs.
<p>Public: consumers may call `.validateSlugPath` to pre-validate a name
before calling `create_channel`.

## Fields

### `MAX_NAME_LENGTH` (`int`)

### `MAX_SEGMENT_LENGTH` (`int`)

### `SEGMENT_PATTERN` (`java.util.regex.Pattern`)

## Constructors

### `private ChannelSlugValidator()`

## Methods

### `public static boolean isValidSegment(java.lang.String segment)`

Returns true iff `segment` is a valid single slug segment.
Rejects UUID-shaped strings — used in contexts where every segment must be
a semantically meaningful slug (e.g. validating auto-channel name patterns).
Note: `.validateSlugPath` does NOT reject UUID-shaped path segments,
only UUID-shaped full names.

#### Parameters

- `segment` (`java.lang.String`)

### `public static java.util.UUID tryParseUuid(java.lang.String s)`

Returns the UUID if `s` parses as one, null otherwise.

#### Parameters

- `s` (`java.lang.String`)

### `public static void validateSlugPath(java.lang.String name)`

Validates that `name` is a well-formed channel slug path.
Every `/`-delimited segment must match `[a-z][a-z0-9]*(-[a-z0-9]+)*`.
Max 80 chars per segment, 200 chars total. UUID-shaped names are rejected.

<p>Note: `'/'` is the path separator; dot (`'.'`) is not a valid segment
character. Use hyphens for compound names (e.g. `quarkmind-scouting-intel`)
or slashes for path hierarchy (e.g. `quarkmind/scouting/intel`).

#### Parameters

- `name` (`java.lang.String`)

#### Throws

- `IllegalArgumentException` — on any violation, with actionable suggestions for
        dot-notation inputs
