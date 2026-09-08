import { Loader2Icon, MapPinIcon } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { Input } from '@/components/ui/input'
import { geocodingEnabled, searchAddress } from '@/lib/geocode'
import type { GeocodeResult } from '@/types'

interface AddressAutocompleteProps {
  value: string
  onValueChange: (value: string) => void
  /** Listeden seçim yapıldığında koordinat + ilçe forma gizlice yazılır. */
  onSelect: (result: GeocodeResult) => void
  invalid?: boolean
  disabled?: boolean
}

const DEBOUNCE_MS = 350

/**
 * Kullanıcı sadece adresi yazar, listeden seçer; koordinatları hiç görmez.
 * Sağlayıcı `lib/geocode.ts` içinde soyutlandı.
 */
export function AddressAutocomplete({
  value,
  onValueChange,
  onSelect,
  invalid,
  disabled,
}: AddressAutocompleteProps) {
  const listId = useId()
  const [suggestions, setSuggestions] = useState<GeocodeResult[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [hata, setHata] = useState<string | null>(null)
  /** Seçimden hemen sonra aynı metin için tekrar arama yapmamak için. */
  const sonSecim = useRef<string | null>(null)

  useEffect(() => {
    if (!geocodingEnabled) return
    if (sonSecim.current === value) return

    const terim = value.trim()
    if (terim.length < 3) {
      setSuggestions((onceki) => (onceki.length > 0 ? [] : onceki))
      setHata((onceki) => (onceki === null ? onceki : null))
      return
    }

    const controller = new AbortController()
    const timer = setTimeout(() => {
      setLoading(true)
      searchAddress(terim, controller.signal)
        .then((sonuclar) => {
          setSuggestions(sonuclar)
          setHata(sonuclar.length === 0 ? 'Eşleşen adres bulunamadı.' : null)
          setOpen(true)
        })
        .catch((error: unknown) => {
          if (error instanceof DOMException && error.name === 'AbortError') return
          setSuggestions([])
          setHata('Adres servisi şu an yanıt vermiyor, adresi elle yazabilirsin.')
        })
        .finally(() => setLoading(false))
    }, DEBOUNCE_MS)

    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [value])

  function handleSelect(result: GeocodeResult) {
    sonSecim.current = result.label
    onValueChange(result.label)
    onSelect(result)
    setOpen(false)
    setSuggestions([])
    setHata(null)
  }

  return (
    <div className="relative">
      <div className="relative">
        <MapPinIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          className="pl-8"
          placeholder="Mahalle, sokak, ilçe…"
          autoComplete="off"
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-invalid={invalid}
          disabled={disabled}
          value={value}
          onChange={(event) => {
            sonSecim.current = null
            onValueChange(event.target.value)
          }}
          onFocus={() => suggestions.length > 0 && setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          onKeyDown={(event) => {
            if (event.key === 'Escape') setOpen(false)
          }}
        />
        {loading ? (
          <Loader2Icon className="absolute top-1/2 right-2.5 size-4 -translate-y-1/2 animate-spin text-muted-foreground" />
        ) : null}
      </div>

      {open && suggestions.length > 0 ? (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-50 mt-1 max-h-56 w-full overflow-y-auto rounded-md border border-border bg-popover p-1 shadow-md"
        >
          {suggestions.map((sonuc) => (
            <li key={`${sonuc.lat},${sonuc.lon}`}>
              <button
                type="button"
                role="option"
                aria-selected={false}
                className="w-full rounded-sm px-2 py-1.5 text-left text-sm hover:bg-accent focus:bg-accent focus:outline-none"
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => handleSelect(sonuc)}
              >
                <span className="block truncate">{sonuc.label}</span>
                {sonuc.ilce ? (
                  <span className="text-xs text-muted-foreground">
                    {sonuc.ilce}
                  </span>
                ) : null}
              </button>
            </li>
          ))}
        </ul>
      ) : null}

      {hata && !open ? (
        <p className="mt-1 text-xs text-muted-foreground">{hata}</p>
      ) : null}
    </div>
  )
}
