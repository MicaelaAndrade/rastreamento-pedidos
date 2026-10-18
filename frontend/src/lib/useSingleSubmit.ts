import { useRef } from 'react'

/**
 * Impede envio duplicado em cliques muito rápidos.
 * O `disabled={isPending}` do botão só vale depois que a tela re-renderiza; dois cliques no mesmo
 * instante passariam antes disso e criariam dois pedidos. A ref muda na hora, sem esperar o render.
 *
 *   const submit = useSingleSubmit()
 *   if (!submit.start()) return
 *   mutation.mutate(dados, { onSettled: submit.done })
 */
export function useSingleSubmit() {
  const busy = useRef(false)
  return {
    start: () => {
      if (busy.current) return false
      busy.current = true
      return true
    },
    done: () => {
      busy.current = false
    },
  }
}
