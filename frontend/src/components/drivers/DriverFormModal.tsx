import { zodResolver } from '@hookform/resolvers/zod'
import { InfoIcon, Loader2Icon } from 'lucide-react'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { AddressAutocomplete } from '@/components/employees/AddressAutocomplete'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { telefonAlani } from '@/lib/validation'
import { createDriver } from '@/store/driversSlice'
import { fetchEmployees } from '@/store/employeesSlice'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { fetchServices } from '@/store/servicesSlice'
import type { DriverInput } from '@/types'

const formSchema = z.object({
  adSoyad: z.string().trim().min(3, 'Ad soyad en az 3 karakter olmalı.'),
  adres: z.string().trim().min(5, 'Adres zorunlu.'),
  telefon: telefonAlani(),
  plaka: z.string().trim(),
  model: z.string().trim(),
  ilce: z.string(),
  lat: z.string(),
  lon: z.string(),
})

type FormValues = z.infer<typeof formSchema>

const BOS_FORM: FormValues = {
  adSoyad: '',
  adres: '',
  telefon: '',
  plaka: '',
  model: '',
  ilce: '',
  lat: '',
  lon: '',
}

function toDriverInput(values: FormValues): DriverInput {
  const lat = Number(values.lat)
  const lon = Number(values.lon)
  const koordinatVar =
    values.lat !== '' &&
    values.lon !== '' &&
    Number.isFinite(lat) &&
    Number.isFinite(lon)

  return {
    adSoyad: values.adSoyad.trim(),
    adres: values.adres.trim(),
    ...(values.telefon ? { telefon: values.telefon.trim() } : {}),
    ...(values.plaka ? { plaka: values.plaka.trim().toLocaleUpperCase('tr') } : {}),
    ...(values.model ? { model: values.model.trim() } : {}),
    ...(values.ilce ? { ilce: values.ilce } : {}),
    ...(koordinatVar ? { lat, lon } : {}),
  }
}

interface DriverFormModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * Şoför ekleme. Kaydedildiğinde <b>yeni bir servis de</b> oluşur — sistemde
 * rotanın başlangıç noktası şoförün ev adresi olduğu için şoförsüz servis ya da
 * servissiz şoför anlamsız.
 */
export function DriverFormModal({ open, onOpenChange }: DriverFormModalProps) {
  const dispatch = useAppDispatch()
  const kaydediliyor = useAppSelector((state) => state.drivers.saving)

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: BOS_FORM,
  })

  const { register, handleSubmit, reset, setValue, watch, formState } = form
  const adres = watch('adres')

  useEffect(() => {
    if (!open) return
    reset(BOS_FORM)
  }, [open, reset])

  const onSubmit = handleSubmit(async (values) => {
    const input = toDriverInput(values)
    try {
      const sofor = await dispatch(createDriver(input)).unwrap()

      // Dengeleme birden fazla servisi etkileyebiliyor; iki listeyi de tazele.
      void dispatch(fetchServices())
      void dispatch(fetchEmployees())

      toast.success(`${sofor.adSoyad} eklendi`, {
        description: `Servis-${sofor.servisId} oluşturuldu ve personel dağılımı dengelendi.`,
      })
      onOpenChange(false)
    } catch (mesaj) {
      toast.error('Şoför eklenemedi', { description: String(mesaj) })
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>Şoför ekle</DialogTitle>
          <DialogDescription>
            Şoförle birlikte yeni bir servis oluşturulur. Servisin rotası bu
            adresten başlar.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          <div className="space-y-1.5">
            <Label htmlFor="soforAdSoyad">Ad Soyad</Label>
            <Input
              id="soforAdSoyad"
              aria-invalid={Boolean(formState.errors.adSoyad)}
              {...register('adSoyad')}
            />
            <FieldError message={formState.errors.adSoyad?.message} />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="soforAdres">Ev adresi</Label>
            <AddressAutocomplete
              value={adres}
              invalid={Boolean(formState.errors.adres)}
              disabled={kaydediliyor}
              onValueChange={(value) => {
                setValue('adres', value, { shouldValidate: true })
                setValue('lat', '')
                setValue('lon', '')
              }}
              onSelect={(sonuc) => {
                setValue('adres', sonuc.label, { shouldValidate: true })
                setValue('ilce', sonuc.ilce)
                setValue('lat', String(sonuc.lat))
                setValue('lon', String(sonuc.lon))
              }}
            />
            <FieldError message={formState.errors.adres?.message} />
            <p className="text-xs text-muted-foreground">
              Sabah rota buradan başlar, akşam burada biter.
            </p>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="soforTelefon">
              Telefon
              <span className="ml-1 font-normal text-muted-foreground">
                (opsiyonel)
              </span>
            </Label>
            <Input
              id="soforTelefon"
              type="tel"
              inputMode="tel"
              placeholder="0532 123 45 67"
              aria-invalid={Boolean(formState.errors.telefon)}
              {...register('telefon')}
            />
            <FieldError message={formState.errors.telefon?.message} />
          </div>

          <fieldset className="space-y-3 rounded-lg border border-border p-3">
            <legend className="px-1 text-xs font-medium tracking-wide text-muted-foreground uppercase">
              Servis aracı
            </legend>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label htmlFor="plaka">Plaka</Label>
                <Input
                  id="plaka"
                  placeholder="34 ABC 123"
                  className="uppercase"
                  {...register('plaka')}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="model">Model</Label>
                <Input
                  id="model"
                  placeholder="Mercedes Sprinter"
                  {...register('model')}
                />
              </div>
            </div>
          </fieldset>

          <p className="flex gap-2 rounded-md bg-muted/50 px-3 py-2 text-xs text-muted-foreground">
            <InfoIcon className="mt-0.5 size-3.5 shrink-0" />
            <span>
              Yeni servis boş doğar. Kaydedildiğinde en uygun personel asgari
              doluluğa kadar bu servise taşınır; geri kalan herkesin servisi
              olduğu gibi kalır.
            </span>
          </p>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={kaydediliyor}
            >
              Vazgeç
            </Button>
            <Button type="submit" disabled={kaydediliyor}>
              {kaydediliyor ? <Loader2Icon className="animate-spin" /> : null}
              Kaydet
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function FieldError({ message }: { message?: string }) {
  if (!message) return null
  return <p className="text-xs text-destructive">{message}</p>
}
