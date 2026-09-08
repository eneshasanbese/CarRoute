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
import {
  ArrowUpDownIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  PencilIcon,
  SearchIcon,
  Trash2Icon,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { EmptyState } from '@/components/common/EmptyState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { serviceColor } from '@/lib/serviceColors'
import type { Employee } from '@/types'

const TUM_DEGERLER = '__hepsi__'

/** Select'ten gelen değer her zaman metin; kolon değeriyle metin olarak eşleşir. */
function metinEsitlik(row: { getValue: (id: string) => unknown }, id: string, value: unknown) {
  return String(row.getValue(id)) === String(value)
}

interface EmployeeTableProps {
  employees: Employee[]
  servisIdleri: number[]
  onEdit: (employee: Employee) => void
  onDelete: (employee: Employee) => void
}

export function EmployeeTable({
  employees,
  servisIdleri,
  onEdit,
  onDelete,
}: EmployeeTableProps) {
  const [sorting, setSorting] = useState<SortingState>([
    { id: 'adSoyad', desc: false },
  ])
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([])
  const [globalFilter, setGlobalFilter] = useState('')

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
        cell: ({ row }) => {
          const servisId = row.original.servisId
          if (servisId == null) {
            return <span className="text-muted-foreground">Atanmadı</span>
          }
          return (
            <Link
              to={`/servis/${servisId}`}
              className="inline-flex items-center gap-1.5 hover:underline"
            >
              <span
                className="size-2.5 rounded-full"
                style={{ backgroundColor: serviceColor(servisId) }}
              />
              Servis-{servisId}
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
              onClick={() => onEdit(row.original)}
            >
              <PencilIcon />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
              aria-label={`${row.original.adSoyad} sil`}
              onClick={() => onDelete(row.original)}
            >
              <Trash2Icon />
            </Button>
          </div>
        ),
      },
    ],
    [onEdit, onDelete],
  )

  const table = useReactTable({
    data: employees,
    columns,
    state: { sorting, columnFilters, globalFilter },
    onSortingChange: setSorting,
    onColumnFiltersChange: setColumnFilters,
    onGlobalFilterChange: setGlobalFilter,
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
    initialState: { pagination: { pageSize: 12 } },
  })

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
            onChange={(event) => setGlobalFilter(event.target.value)}
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
          options={servisIdleri.map((id) => ({
            value: String(id),
            label: `Servis-${id}`,
          }))}
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
              setColumnFilters([])
              setGlobalFilter('')
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
              <TableRow key={row.id}>
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

      <div className="flex items-center justify-between text-sm text-muted-foreground">
        <span>
          {table.getFilteredRowModel().rows.length} kayıt
          {filtreVar ? ` (toplam ${employees.length})` : ''}
        </span>
        <div className="flex items-center gap-2">
          <span className="tabular-nums">
            Sayfa {table.getState().pagination.pageIndex + 1} /{' '}
            {Math.max(1, table.getPageCount())}
          </span>
          <Button
            variant="outline"
            size="icon"
            aria-label="Önceki sayfa"
            onClick={() => table.previousPage()}
            disabled={!table.getCanPreviousPage()}
          >
            <ChevronLeftIcon />
          </Button>
          <Button
            variant="outline"
            size="icon"
            aria-label="Sonraki sayfa"
            onClick={() => table.nextPage()}
            disabled={!table.getCanNextPage()}
          >
            <ChevronRightIcon />
          </Button>
        </div>
      </div>
    </div>
  )
}

function SortableHeader({
  column,
  children,
}: {
  column: { toggleSorting: (desc?: boolean) => void; getIsSorted: () => false | 'asc' | 'desc' }
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      className="inline-flex items-center gap-1 uppercase hover:text-foreground"
      onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
    >
      {children}
      <ArrowUpDownIcon className="size-3" />
    </button>
  )
}

function FilterSelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  options: Array<{ value: string; label: string }>
}) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className="w-auto min-w-36">
        <SelectValue placeholder={label} />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={TUM_DEGERLER}>{label}: hepsi</SelectItem>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
