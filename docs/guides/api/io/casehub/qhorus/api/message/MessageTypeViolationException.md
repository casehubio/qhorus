# io.casehub.qhorus.api.message.MessageTypeViolationException

**Package:** `io.casehub.qhorus.api.message`

**Kind:** `class`

## Constructors

### `private MessageTypeViolationException(java.lang.String message)`

#### Parameters

- `message` (`java.lang.String`)

### `public MessageTypeViolationException(java.lang.String channel, io.casehub.qhorus.api.message.MessageType attempted, java.lang.String allowed)`

#### Parameters

- `channel` (`java.lang.String`)
- `attempted` (`io.casehub.qhorus.api.message.MessageType`)
- `allowed` (`java.lang.String`)

## Methods

### `public static io.casehub.qhorus.api.message.MessageTypeViolationException denied(java.lang.String channel, io.casehub.qhorus.api.message.MessageType attempted, java.lang.String denied)`

#### Parameters

- `channel` (`java.lang.String`)
- `attempted` (`io.casehub.qhorus.api.message.MessageType`)
- `denied` (`java.lang.String`)
