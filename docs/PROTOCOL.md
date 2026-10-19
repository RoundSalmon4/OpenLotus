# Protocol notes

Everything here was checked against the stock app and the public ELK-BLEDOM
write ups. Values are raw bytes.

## GATT

- service `0000fff0-0000-1000-8000-00805f9b34fb`
- write characteristic `0000fff3-0000-1000-8000-00805f9b34fb`

Writes go out as write without response when the characteristic supports it.
The stock app requests an MTU of 63 after service discovery.

## Frame

Every command is 9 bytes:

```
0x7E | b1 | b2 | b3 | b4 | b5 | b6 | b7 | 0xEF
```

Unused slots are usually 0xFF. The position of each argument depends on the
command, so there is no single template. The builders in the protocol module
spell each frame out.

## Commands

| Action | Frame |
| --- | --- |
| power on | `7E 04 04 01 00 01 FF 00 EF` |
| power off | `7E 04 04 00 00 00 FF 00 EF` |
| brightness | `7E 04 01 <0-100> <light mode> FF FF 00 EF` |
| speed | `7E 04 02 <0-100> FF FF FF 00 EF` |
| color | `7E 07 05 03 <r> <g> <b> 10 EF` |
| color from music | `7E 07 05 03 <r> <g> <b> 20 EF` |
| color temperature | `7E 06 05 02 <warm> <cold> FF 08 EF` |
| single color | `7E 05 05 01 <value> FF FF 08 EF` |
| mode | `7E 05 03 <0x80 + index> 03 FF FF 00 EF` |
| pin order | `7E 06 81 <r pin> <g pin> <b pin> FF 00 EF` |
| mic sensitivity | `7E 04 06 <0-100> FF FF FF 00 EF` |
| mic on/off | `7E 04 07 <0 or 1> FF FF FF 00 EF` |
| clock | `7E 07 83 <h> <m> <s> <weekday> FF EF` |
| schedule | `7E 08 82 <h> <m> <s> <mode> <weekdays> EF` |

Light mode values: 0 all, 1 RGB, 2 white, 3 color temperature, 4 laser.

## Modes

There are 29 modes. The wire value is the index plus 0x80, so they run from
0x80 to 0x9C. Index 0 to 6 are the static colors (red, blue, green, cyan,
yellow, purple, white). Index 7 onward are the dynamic effects: jump, cross
fade, gradual change and strobe, in that order, ending with white strobe at
0x9C.

## Legacy dialect

The community write ups use `0x00` in byte 1 instead of the values above:

- power on `7E 00 04 F0 00 01 FF 00 EF`
- power off `7E 00 04 00 00 00 FF 00 EF`
- color `7E 00 05 03 <r> <g> <b> 00 EF`
- brightness `7E 00 01 <0-100> 00 00 00 00 EF`
- mode `7E 00 03 <code> 03 00 00 00 EF`
- speed `7E 00 02 <0-100> 00 00 00 00 EF`

The protocol module keeps both sets. Which one a strip accepts needs a quick
test on real hardware.

## Sequences

I could not find a firmware command for a timed multi color sequence. The stock
app stores a custom palette and animates colors from the phone, so OpenLotus
does the same: a static color frame is written every dwell interval.

## Encrypted variant

The stock app has a second path for devices whose advertised name contains
`ELK-*`. Frames go out as a 21 byte encrypted packet and only some commands are
encrypted. Not implemented here yet.
