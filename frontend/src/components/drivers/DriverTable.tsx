import {
  type ColumnDef,
  type ColumnFiltersState,
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  type SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { PencilIcon, SearchIcon, Trash2Icon } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { EmptyState } from '@/components/common/EmptyState'
import {
  FilterSelect,
  SortableHeader,
  TablePagination,
} from '@/components/common/TableParts'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  usePageIndexClamp,
  useStablePagination,
} from '@/hooks/useStablePagination'
import { CAPACITY_META, capacityLevel } from '@/lib/capacity'
import { serviceColor } from '@/lib/serviceColors'
import { servisAdi, servisAdiKarsilastir } from '@/lib/serviceName'
import { metinEsitlik, TUM_DEGERLER } from '@/lib/tableFilters'
import { cn } from '@/lib/utils'
import type { Driver, Service } from '@/types'

const SAYFA_BOYUTU = 12

interface DriverTableProps {
  drivers: Driver[]
  /** Servis doluluğunu göstermek için; şoförün servis id'siyle eşleşir. */
  services: Service[]
  onEdit: (driver: Driver) => void
  onDelete: (driver: Driver) => void
  /** Satıra tıklanınca detay kartını açar. */
  onSelect: (driver: Driver) => void
}

export function DriverTable({
  drivers,
  services,
  onEdit,
  onDelete,
  onSelect,
}: DriverTableProps) {
  const [sorting, setSorting] = useState<SortingState>([
    { id: 'adSoyad', desc: false },
  ])
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([])
  const [globalFilter, setGlobalFilter] = useState('')
  const { pagination, setPagination, resetPageIndex } =
    useStablePagination(SAYFA_BOYUTU)

  const ilceler = useMemo(
    () => [...new Set(drivers.map((d) => d.ilce))].sort((a, b) => a.localeCompare(b, 'tr')),
    [drivers],
  )

  const servisler = useMemo(
    () => new Map(services.map((service) => [service.id, service])),
    [services],
  )

  const columns = useMemo<ColumnDef<Driver>[]>(
    () => [
      {
        accessorKey: 'adSoyad',
        header: ({ column }) => (
          <SortableHeader column={column}>Ad Soyad</SortableHeader>
        ),
        cell: ({ row }) => (
          <span className="font-medium">{row.original.adSoyad}</span>
        ),
      },
      {
        accessorKey: 'telefon',
        header: 'Telefon',
        cell: ({ row }) =>
          row.original.telefon ? (
            <span className="tabular-nums">{row.original.telefon}</span>
          ) : (
            <span className="text-muted-foreground">—</span>
          ),
      },
      {
        accessorKey: 'ilce',
        header: 'İlçe',
        filterFn: metinEsitlik,
      },
      {
        accessorKey: 'adres',
        header: 'Ev adresi',
        cell: ({ row }) => (
          <span
            className="block max-w-[22rem] truncate text-muted-foreground"
            title={row.original.adres}
          >
            {row.original.adres}
          </span>
        ),
      },
      {
        // Servisin adı plakası; sütun ikisini tek yerde gösteriyor.
        id: 'servis',
        accessorFn: (driver) => servisAdi(driver.plaka),
        header: ({ column }) => (
          <SortableHeader column={column}>Servis</SortableHeader>
        ),
        sortingFn: (a, b) =>
          servisAdiKarsilastir(
            servisAdi(a.original.plaka),
            servisAdi(b.original.plaka),
          ),
        cell: ({ row }) => {
          const servisId = row.original.servisId
          if (servisId == null) {
            return <span className="text-muted-foreground">Servisi yok</span>
          }
          const servis = servisler.get(servisId)
          return (
            <div className="flex items-center gap-2">
              <Link
                to={`/servis/${servisId}`}
                className="inline-flex items-center gap-1.5 font-medium tabular-nums hover:underline"
                onClick={(event) => event.stopPropagation()}
              >
                <span
                  className="size-2.5 rounded-full"
                  style={{ backgroundColor: serviceColor(servisId) }}
                />
                {servisAdi(row.original.plaka)}
              </Link>
              {servis ? (
                <span
                  className={cn(
                    'text-xs tabular-nums',
                    CAPACITY_META[capacityLevel(servis)].text,
                  )}
                  title="Doluluk"
                >
                  {servis.kisiSayisi}/{servis.maxKapasite}
                </span>
              ) : null}
            </div>
          )
        },
      },
      {
        accessorKey: 'model',
        header: 'Araç',
        cell: ({ row }) =>
          row.original.model ? (
            <span className="text-muted-foreground">{row.original.model}</span>
          ) : (
            <span className="text-muted-foreground">—</span>
          ),
      },
      {
        id: 'islemler',
        header: '',
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex justify-end gap-1">
            <Button
              variant="ghost"
              size="icon"
              aria-label={`${row.original.adSoyad} düzenle`}
              onClick={(event) => {
                // Satır tıklaması detay kartını açıyor; buton onu tetiklemesin.
                event.stopPropagation()
                onEdit(row.original)
              }}
            >
              <PencilIcon />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
              aria-label={`${row.original.adSoyad} sil`}
              onClick={(event) => {
                event.stopPropagation()
                onDelete(row.original)
              }}
            >
              <Trash2Icon />
            </Button>
          </div>
        ),
      },
    ],
    [servisler, onEdit, onDelete],
  )

  const table = useReactTable({
    data: drivers,
    columns,
    state: { sorting, columnFilters, globalFilter, pagination },
    // Liste periyodik tazeleniyor; sayfa yalnızca kullanıcı arayınca,
    // filtreleyince ya da sıralayınca başa döner.
    autoResetPageIndex: false,
    onPaginationChange: setPagination,
    onSortingChange: (updater) => {
      setSorting(updater)
      resetPageIndex()
    },
    onColumnFiltersChange: (updater) => {
      setColumnFilters(updater)
      resetPageIndex()
    },
    onGlobalFilterChange: (updater) => {
      setGlobalFilter(updater)
      resetPageIndex()
    },
    globalFilterFn: (row, _columnId, value) => {
      const terim = String(value).toLocaleLowerCase('tr')
      const { adSoyad, adres, plaka } = row.original
      return (
        adSoyad.toLocaleLowerCase('tr').includes(terim) ||
        adres.toLocaleLowerCase('tr').includes(terim) ||
        (plaka ?? '').toLocaleLowerCase('tr').includes(terim)
      )
    },
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
  })

  usePageIndexClamp(table)

  const ilceFiltresi =
    (table.getColumn('ilce')?.getFilterValue() as string) ?? TUM_DEGERLER
  const filtreVar = columnFilters.length > 0 || globalFilter.length > 0

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-56 flex-1">
          <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-8"
            placeholder="Ad, adres veya plaka ara…"
            value={globalFilter}
            onChange={(event) => table.setGlobalFilter(event.target.value)}
          />
        </div>

        <FilterSelect
          label="İlçe"
          value={ilceFiltresi}
          onChange={(value) =>
            table
              .getColumn('ilce')
              ?.setFilterValue(value === TUM_DEGERLER ? undefined : value)
          }
          options={ilceler.map((ilce) => ({ value: ilce, label: ilce }))}
        />

        {filtreVar ? (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              table.setColumnFilters([])
              table.setGlobalFilter('')
            }}
          >
            Filtreleri temizle
          </Button>
        ) : null}
      </div>

      <div className="rounded-xl border border-border bg-card">
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead key={header.id}>
                    {header.isPlaceholder
                      ? null
                      : flexRender(
                          header.column.columnDef.header,
                          header.getContext(),
                        )}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows.map((row) => (
              <TableRow
                key={row.id}
                tabIndex={0}
                role="button"
                aria-label={`${row.original.adSoyad} detayı`}
                className="cursor-pointer focus-visible:bg-muted/60 focus-visible:outline-none"
                onClick={() => onSelect(row.original)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    onSelect(row.original)
                  }
                }}
              >
                {row.getVisibleCells().map((cell) => (
                  <TableCell key={cell.id}>
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>

        {table.getRowModel().rows.length === 0 ? (
          <EmptyState
            className="border-0"
            title={filtreVar ? 'Filtreye uyan şoför yok' : 'Henüz şoför eklenmemiş'}
            description={
              filtreVar
                ? 'Filtreleri temizleyip tekrar deneyebilirsin.'
                : '"+ Şoför Ekle" ile ilk şoförü ekleyebilirsin.'
            }
          />
        ) : null}
      </div>

      <TablePagination
        table={table}
        toplam={drivers.length}
        filtreVar={filtreVar}
      />
    </div>
  )
}
