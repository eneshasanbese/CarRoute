import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2Icon } from 'lucide-react'
import { useEffect } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'
import { AddressAutocomplete } from '@/components/AddressAutocomplete'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { useCreateEmployee, useUpdateEmployee } from '@/hooks/useEmployees'
import type { Employee, EmployeeInput } from '@/types'

/**
 * Sayısal alanlar formda metin olarak tutulup gönderim öncesi sayıya çevriliyor;
 * böylece boş/geçersiz girişte Türkçe hata mesajı verebiliyoruz.
 */
function sayiAlani(min: number, max: number, label: string) {
  return z
    .string()
    .trim()
    .min(1, `${label} zorunlu.`)
    .refine((v) => /^\d+$/.test(v), `${label} sayı olmalı.`)
    .refine((v) => {
      const n = Number(v)
      return n >= min && n <= max
    }, `${label} ${min}-${max} arasında olmalı.`)
}

const formSchema = z
  .object({
    adSoyad: z.string().trim().min(3, 'Ad soyad en az 3 karakter olmalı.'),
    adres: z.string().trim().min(5, 'Adres zorunlu.'),
    cinsiyet: z.enum(['Kadın', 'Erkek']),
    yas: sayiAlani(18, 70, 'Yaş'),
    arabaliMi: z.boolean(),
    cocukVarMi: z.boolean(),
    ilce: z.string(),
    lat: z.string(),
    lon: z.string(),
  })

type FormValues = z.infer<typeof formSchema>

const BOS_FORM: FormValues = {
  adSoyad: '',
  adres: '',
  cinsiyet: 'Kadın',
  yas: '',
  arabaliMi: false,
  cocukVarMi: false,
  ilce: '',
  lat: '',
  lon: '',
}

function toFormValues(employee: Employee): FormValues {
  return {
    adSoyad: employee.adSoyad,
    adres: employee.adres,
    cinsiyet: employee.cinsiyet,
    yas: String(employee.yas),
    arabaliMi: employee.arabaliMi,
    cocukVarMi: employee.cocukVarMi,
    ilce: employee.ilce,
    lat: String(employee.lat),
    lon: String(employee.lon),
  }
}

function toEmployeeInput(values: FormValues): EmployeeInput {
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
    cinsiyet: values.cinsiyet,
    yas: Number(values.yas),
    arabaliMi: values.arabaliMi,
    cocukVarMi: values.cocukVarMi,
    ...(values.ilce ? { ilce: values.ilce } : {}),
    ...(koordinatVar ? { lat, lon } : {}),
  }
}

interface EmployeeFormModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Doluysa düzenleme, boşsa ekleme modu. */
  employee?: Employee | null
}

export function EmployeeFormModal({
  open,
  onOpenChange,
  employee,
}: EmployeeFormModalProps) {
  const duzenleme = Boolean(employee)
  const create = useCreateEmployee()
  const update = useUpdateEmployee()
  const kaydediliyor = create.isPending || update.isPending

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: BOS_FORM,
  })

  const { control, register, handleSubmit, reset, setValue, watch, formState } =
    form
  const adres = watch('adres')

  useEffect(() => {
    if (!open) return
    reset(employee ? toFormValues(employee) : BOS_FORM)
  }, [open, employee, reset])

  const onSubmit = handleSubmit(async (values) => {
    const input = toEmployeeInput(values)
    try {
      if (employee) {
        await update.mutateAsync({ id: employee.id, input })
      } else {
        await create.mutateAsync(input)
      }
      onOpenChange(false)
    } catch {
      // Hata bildirimi mutation hook'larındaki toast ile veriliyor.
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>
            {duzenleme ? 'Personeli düzenle' : 'Personel ekle'}
          </DialogTitle>
          <DialogDescription>
            Kaydedildiğinde kişi en uygun servise atanır ve o servisin rotası
            yeniden hesaplanır.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          <div className="space-y-1.5">
            <Label htmlFor="adSoyad">Ad Soyad</Label>
            <Input
              id="adSoyad"
              aria-invalid={Boolean(formState.errors.adSoyad)}
              {...register('adSoyad')}
            />
            <FieldError message={formState.errors.adSoyad?.message} />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="adres">Adres</Label>
            <AddressAutocomplete
              value={adres}
              invalid={Boolean(formState.errors.adres)}
              disabled={kaydediliyor}
              onValueChange={(value) => {
                setValue('adres', value, { shouldValidate: true })
                // Serbest yazımda eski koordinat geçersiz; backend geocode eder.
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
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="cinsiyet">Cinsiyet</Label>
              <Controller
                control={control}
                name="cinsiyet"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="cinsiyet">
                      <SelectValue placeholder="Seç" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="Kadın">Kadın</SelectItem>
                      <SelectItem value="Erkek">Erkek</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="yas">Yaş</Label>
              <Input
                id="yas"
                inputMode="numeric"
                aria-invalid={Boolean(formState.errors.yas)}
                {...register('yas')}
              />
              <FieldError message={formState.errors.yas?.message} />
            </div>
          </div>

          <div className="space-y-3 rounded-lg border border-border p-3">
            <Controller
              control={control}
              name="arabaliMi"
              render={({ field }) => (
                <div className="flex items-center justify-between">
                  <Label htmlFor="arabaliMi" className="font-normal">
                    Kendi aracı var
                  </Label>
                  <Switch
                    id="arabaliMi"
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                </div>
              )}
            />

            <Controller
              control={control}
              name="cocukVarMi"
              render={({ field }) => (
                <div className="flex items-center justify-between">
                  <Label htmlFor="cocukVarMi" className="font-normal">
                    Çocuğu var
                  </Label>
                  <Switch
                    id="cocukVarMi"
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                </div>
              )}
            />
          </div>

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
              {duzenleme ? 'Güncelle' : 'Kaydet'}
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
