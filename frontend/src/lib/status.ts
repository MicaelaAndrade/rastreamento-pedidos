import type { OrderStatus } from '../api/types'

export const STATUS_LABEL: Record<OrderStatus, string> = {
  RECEBIDO: 'Recebido',
  EM_PREPARO: 'Em preparo',
  SAIU_PARA_ENTREGA: 'Saiu para entrega',
  ENTREGUE: 'Entregue',
  CANCELADO: 'Cancelado',
}

/** O caminho "feliz" de um pedido, na ordem em que acontece. */
export const ROUTE: OrderStatus[] = ['RECEBIDO', 'EM_PREPARO', 'SAIU_PARA_ENTREGA', 'ENTREGUE']

/**
 * Texto do botão que leva o pedido ao próximo status.
 * As regras de quais transições valem ficam no back-end (campo allowedNextStatuses);
 * aqui só escolhemos como chamar cada ação.
 */
export const ADVANCE_LABEL: Partial<Record<OrderStatus, string>> = {
  EM_PREPARO: 'Iniciar preparo',
  SAIU_PARA_ENTREGA: 'Enviar para entrega',
  ENTREGUE: 'Confirmar entrega',
}
