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
import { Badge } from '@/components/ui/badge'
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
import { useServiceColor } from '@/hooks/useServiceColor'
import { servisAdiKarsilastir } from '@/lib/serviceName'
import { metinEsitlik, TUM_DEGERLER } from '@/lib/tableFilters'
import type { Employee } from '@/types'

const SAYFA_BOYUTU = 12

interface EmployeeTableProps {
  employees: Employee[]
  /** Servis id -> ad (plaka); servis sütunu ve filtresi için. */
  servisAdlari: Record<number, string>
  onEdit: (employee: Employee) => void
  onDelete: (employee: Employee) => void
  /** Satıra tıklanınca detay kartını açar. */
  onSelect: (employee: Employee) => void
}

export function EmployeeTable({
  employees,
  servisAdlari,
  onEdit,
  onDelete,
  onSelect,
}: EmployeeTableProps) {
  const [sorting, setSorting] = useState<SortingState>([
    { id: 'adSoyad', desc: false },
  ])
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([])
  const [globalFilter, setGlobalFilter] = useState('')
  const { pagination, setPagination, resetPageIndex } =
    useStablePagination(SAYFA_BOYUTU)

  const serviceColor = useServiceColor()
  const ilceler = useMemo(
    () => [...new Set(employees.map((e) => e.ilce))].sort((a, b) => a.localeCompare(b, 'tr')),
    [employees],
  )

  const columns = useMemo<ColumnDef<Employee>[]>(
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
        accessorKey: 'cinsiyet',
        header: 'Cinsiyet',
        filterFn: metinEsitlik,
      },
      {
        accessorKey: 'yas',
        header: ({ column }) => (
          <SortableHeader column={column}>Yaş</SortableHeader>
        ),
        cell: ({ row }) => (
          <span className="tabular-nums">{row.original.yas}</span>
        ),
      },
      {
        accessorKey: 'ilce',
        header: 'İlçe',
        filterFn: metinEsitlik,
      },
      {
        accessorKey: 'adres',
        header: 'Adres',
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
        accessorKey: 'arabaliMi',
        header: 'Araçlı',
        filterFn: metinEsitlik,
        cell: ({ row }) =>
          row.original.arabaliMi ? (
            <Badge variant="secondary">Evet</Badge>
          ) : (
            <span className="text-muted-foreground">—</span>
          ),
      },
      {
        accessorKey: 'cocukVarMi',
        header: 'Çocuk',
        filterFn: metinEsitlik,
        cell: ({ row }) =>
          row.original.cocukVarMi ? (
            <Badge variant="secondary">Var</Badge>
          ) : (
            <span className="text-muted-foreground">—</span>
          ),
      },
      {
        accessorKey: 'servisId',
        header: ({ column }) => (
          <SortableHeader column={column}>Servis</SortableHeader>
        ),
        filterFn: metinEsitlik,
        // Sütun id'yi taşıyor (filtre id ile eşleşiyor) ama sıralama görünen ada göre.
        sortingFn: (a, b) =>
          servisAdiKarsilastir(
            servisAdlari[a.original.servisId ?? -1] ?? '',
            servisAdlari[b.original.servisId ?? -1] ?? '',
          ),
        cell: ({ row }) => {
          const servisId = row.original.servisId
          if (servisId == null) {
            return <span className="text-muted-foreground">Atanmadı</span>
          }
          return (
            <Link
              to={`/servis/${servisId}`}
              className="inline-flex items-center gap-1.5 hover:underline"
              onClick={(event) => event.stopPropagation()}
            >
              <span
                className="size-2.5 rounded-full"
                style={{ backgroundColor: serviceColor(servisId) }}
              />
              {servisAdlari[servisId] ?? 'Servis'}
            </Link>
          )
        },
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
    [servisAdlari, serviceColor, onEdit, onDelete],
  )

  const table = useReactTable({
    data: employees,
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
      return (
        row.original.adSoyad.toLocaleLowerCase('tr').includes(terim) ||
        row.original.adres.toLocaleLowerCase('tr').includes(terim)
      )
    },
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
  })

  usePageIndexClamp(table)

  function filtreDegeri(columnId: string) {
    return (table.getColumn(columnId)?.getFilterValue() as string) ?? TUM_DEGERLER
  }

  function filtreAta(columnId: string, value: string) {
    table
      .getColumn(columnId)
      ?.setFilterValue(value === TUM_DEGERLER ? undefined : value)
  }

  const filtreVar = columnFilters.length > 0 || globalFilter.length > 0

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-56 flex-1">
          <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-8"
            placeholder="Ad veya adres ara…"
            value={globalFilter}
            onChange={(event) => table.setGlobalFilter(event.target.value)}
          />
        </div>

        <FilterSelect
          label="İlçe"
          value={filtreDegeri('ilce')}
          onChange={(value) => filtreAta('ilce', value)}
          options={ilceler.map((ilce) => ({ value: ilce, label: ilce }))}
        />
        <FilterSelect
          label="Cinsiyet"
          value={filtreDegeri('cinsiyet')}
          onChange={(value) => filtreAta('cinsiyet', value)}
          options={[
            { value: 'Kadın', label: 'Kadın' },
            { value: 'Erkek', label: 'Erkek' },
          ]}
        />
        <FilterSelect
          label="Servis"
          value={filtreDegeri('servisId')}
          onChange={(value) => filtreAta('servisId', value)}
          options={Object.entries(servisAdlari)
            .map(([id, ad]) => ({ value: id, label: ad }))
            .sort((a, b) => servisAdiKarsilastir(a.label, b.label))}
        />
        <FilterSelect
          label="Araçlı"
          value={filtreDegeri('arabaliMi')}
          onChange={(value) => filtreAta('arabaliMi', value)}
          options={[
            { value: 'true', label: 'Araçlı' },
            { value: 'false', label: 'Araçsız' },
          ]}
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
            title={
              filtreVar
                ? 'Filtreye uyan personel yok'
                : 'Henüz personel eklenmemiş'
            }
            description={
              filtreVar
                ? 'Filtreleri temizleyip tekrar deneyebilirsin.'
                : '"+ Personel Ekle" ile ilk kişiyi ekleyebilirsin.'
            }
          />
        ) : null}
      </div>

      <TablePagination
        table={table}
        toplam={employees.length}
        filtreVar={filtreVar}
      />
    </div>
  )
}
