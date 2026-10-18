# Rastreamento de pedidos de delivery

Sistema simplificado para acompanhar pedidos de delivery: cadastro e login de usuários, criação de pedidos e atualização de status de `RECEBIDO` até `ENTREGUE` (ou `CANCELADO`).

- **Back-end:** Java 21, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA, SQLite, Flyway
- **Front-end:** React 19, TypeScript, Vite, TanStack Query
- **Testes:** 95 no back-end (JUnit 5, MockMvc) e 68 no front-end (Vitest, Testing Library)

## Como rodar

Pré-requisitos: **Java 21** e **Node 22**. O Maven não precisa estar instalado: o projeto usa o Maven Wrapper (`./mvnw`).

**1. API** (porta 8080)

```bash
cd backend
./mvnw spring-boot:run
```

O banco SQLite (`backend/pedidos.db`) e as tabelas são criados automaticamente na primeira execução.

**2. Front-end** (porta 5173), em outro terminal

```bash
cd frontend
npm install
npm run dev
```

Abra http://localhost:5173, clique em **Criar conta** e comece a cadastrar pedidos. Em desenvolvimento o Vite repassa as chamadas `/api` para a porta 8080, então não há configuração de CORS a fazer.

**Documentação interativa da API:** http://localhost:8080/swagger-ui.html. Faça o cadastro ou login em *Autenticação*, clique em **Authorize** e cole o token.

### Sem instalar nada: GitHub Codespaces

O repositório tem um `.devcontainer` com Java 21, Maven e Node 22. No GitHub, clique em **Code → Codespaces → Create codespace on main** e, quando abrir, rode os dois comandos acima em dois terminais. A porta 5173 abre no navegador. O Swagger fica no endereço da porta 8080, com `/swagger-ui.html` no final.

### Testes

```bash
cd backend && ./mvnw test        # 95 testes: regras de status, domínio, API ponta a ponta e casos de borda
cd frontend && npm test          # 68 testes: rastreio de status, ações do pedido, sessão, formulário
```

### Configuração (variáveis de ambiente, todas opcionais)

| Variável | Padrão | Para quê |
|---|---|---|
| `JWT_SECRET` | chave de desenvolvimento | Chave de assinatura do token (mín. 32 caracteres). A padrão é pública: a API avisa no log ao usá-la. **Defina em produção.** |
| `JWT_EXPIRATION` | `8h` | Validade do token |
| `DB_PATH` | `pedidos.db` | Arquivo do banco SQLite |
| `CORS_ALLOWED_ORIGINS` | `localhost` em qualquer porta | Origens liberadas (aceita `*` e `[*]` para porta). Cobre o desenvolvimento local e o GitHub Codespaces, que entrega as requisições com Origin `localhost`. **Em produção, informe o endereço real do front** |

## API

Todas as rotas, exceto cadastro e login, exigem o header `Authorization: Bearer <token>`.

| Método | Rota | O que faz |
|---|---|---|
| `POST` | `/api/auth/register` | Cria a conta (nome, e-mail, senha) e já devolve o token |
| `POST` | `/api/auth/login` | Login com e-mail e senha |
| `GET` | `/api/auth/me` | Dados do usuário logado |
| `POST` | `/api/orders` | Cria um pedido (cliente, endereço, itens). Começa como `RECEBIDO` |
| `GET` | `/api/orders` | Lista os pedidos, mais recentes primeiro. Filtro opcional: `?status=EM_PREPARO` |
| `GET` | `/api/orders/{id}` | Busca um pedido pelo ID |
| `PATCH` | `/api/orders/{id}/status` | Muda o status: `{"status": "EM_PREPARO", "expectedVersion": 0}` (`expectedVersion` é opcional, veja *Concorrência*) |

<details>
<summary>Exemplo: criar um pedido</summary>

```json
POST /api/orders
{
  "customer": { "name": "Ana Souza", "phone": "(16) 99999-0000" },
  "deliveryAddress": {
    "street": "Rua das Flores", "number": "120", "complement": "Apto 3",
    "neighborhood": "Centro", "city": "Franca", "zipCode": "14400-000"
  },
  "items": [
    { "name": "X-Burger", "quantity": 2, "unitPrice": 25.90 },
    { "name": "Refrigerante", "quantity": 1, "unitPrice": 7.50 }
  ]
}
```

A resposta (`201 Created`, com header `Location`) traz o pedido completo, incluindo `total`, o histórico de status, `allowedNextStatuses` (para quais status o pedido pode ir agora) e `version`.

Os tipos são conferidos à risca: quantidade `1.5` ou `"2"`, preço em texto e status numérico (`{"status": 4}`) respondem `400` dizendo o campo, em vez de serem convertidos em silêncio. O preço precisa ser maior que zero, com até 2 casas decimais.

</details>

**Erros** seguem o formato [Problem Details (RFC 9457)](https://www.rfc-editor.org/rfc/rfc9457), com mensagens em português:

| Situação | Status |
|---|---|
| Campos inválidos (com a lista `errors` dizendo quais) | `400` |
| Sem token, token inválido ou expirado, usuário do token não existe mais, senha errada | `401` |
| Pedido não encontrado | `404` |
| Transição de status não permitida, pedido alterado por outra pessoa, e-mail já cadastrado | `409` |
| Erro inesperado (sem detalhes internos; a causa vai para o log) | `500` |

## Regras de status

```
RECEBIDO ──► EM_PREPARO ──► SAIU_PARA_ENTREGA ──► ENTREGUE
    │             │
    ▼             ▼
CANCELADO     CANCELADO
```

- Não dá para pular etapas nem voltar.
- O cancelamento só vale até o pedido sair para entrega. Depois disso o entregador já está a caminho, e cancelar exigiria outro fluxo (devolução, estorno).
- `ENTREGUE` e `CANCELADO` são finais.
- Toda mudança fica registrada no histórico, com data, hora e quem fez.

## Decisões técnicas

**Regras de negócio no domínio.** A entidade `Order` só muda de status pelo método `changeStatus`, que valida a transição e grava o histórico. As transições ficam em `OrderStatus.nextStatuses()`, um `switch` exaustivo: se alguém criar um status novo, o código só compila depois que as regras dele forem definidas. O service apenas coordena (busca, salva, converte).

**Uma única fonte da regra.** A API devolve `allowedNextStatuses` em cada pedido, e o front mostra só os botões dessas ações. Assim a regra de transição não é duplicada no React.

**Autenticação stateless com JWT.** O login emite um token HS256. A validação (assinatura, emissor, validade) é feita pelo Spring Security como *resource server*, sem filtro escrito à mão. Em toda requisição, a API também confere se o token pertence a um usuário que existe, com o mesmo e-mail, e se a conta é anterior ao token: num banco recriado, os IDs recomeçam em 1, e um token antigo não pode valer como a pessoa que ganhou o mesmo ID. Senhas com BCrypt, limitadas a 72 **bytes** no cadastro e no login, o limite real do algoritmo (letras com acento e emojis ocupam mais de um byte; sem o limite no login, o BCrypt ignoraria o que vem depois). Dois cadastros simultâneos com o mesmo e-mail criam uma conta só; o outro recebe 409. O e-mail é normalizado (minúsculas), e o login responde a mesma mensagem para e-mail inexistente e senha errada, para não revelar quais e-mails têm conta.

**Todos os usuários veem todos os pedidos.** O sistema foi pensado como um painel de operação (cozinha, expedição), em que qualquer pessoa da equipe acompanha e atualiza os pedidos. Quem criou o pedido e quem fez cada mudança fica registrado.

**SQLite com cuidado nos tipos.** O SQLite não tem tipo decimal exato nem tipo de data:
- valores em dinheiro são guardados em **centavos inteiros** (`MoneyConverter`), para `25.90` nunca virar `25.8999...`;
- datas são guardadas como texto ISO-8601 em UTC (`InstantConverter`);
- o pool tem **uma conexão**, porque o SQLite aceita um escritor por vez. Por isso o hash da senha (BCrypt, ~80 ms) roda fora de transação: cadastro e login não seguram a conexão enquanto calculam.

O schema é versionado com **Flyway** (`db/migration`), com `CHECK` nos status e quantidades, em vez de deixar o Hibernate gerar as tabelas.

**Concorrência.** Cada pedido tem uma `version`, que muda a cada alteração. Ao mudar o status, o front envia a versão que a tela está mostrando (`expectedVersion`). Se outra pessoa alterou o pedido nesse meio-tempo, por exemplo iniciou o preparo enquanto alguém clicava em cancelar, a API responde `409` em vez de aplicar a mudança sobre um estado que a pessoa não viu, e a tela se atualiza. O campo é opcional para quem usa a API direto (Swagger, curl). O `@Version` do JPA fica como segunda proteção caso o banco passe a aceitar escritas em paralelo; com o SQLite e uma conexão só, elas já acontecem uma de cada vez.

**Endereço pelo CEP.** No novo pedido, ao digitar o CEP, rua, bairro e cidade são preenchidos pela [ViaCEP](https://viacep.com.br), um serviço público. Para um sistema de delivery, endereço digitado errado é entrega que atrasa, então vale ajudar. Como depende de um serviço de fora, a busca é só uma ajuda: se o CEP não existir ou a ViaCEP estiver fora do ar, aparece um aviso e os campos continuam livres para preencher à mão. A busca nunca apaga o que a pessoa já digitou, e uma resposta atrasada de um CEP antigo é ignorada. A chamada fica no front-end para não acoplar a API a um serviço externo. Num sistema maior, eu passaria a busca pelo back-end, com cache e fornecedor reserva.

**Front-end.** TanStack Query cuida de cache, recarga e estados de carregamento. A lista se atualiza a cada 15 segundos para refletir mudanças feitas por outras pessoas, e os detalhes abertos usam os mesmos dados, então nunca ficam desatualizados em relação à lista. Cada pedido aparece como uma "rota" com o horário em que passou por cada status. Telefone, CEP e preço têm máscara (o preço é digitado só com números e os centavos se ajustam sozinhos; colar "25,90" também funciona). Fechar o novo pedido com dados preenchidos pede confirmação, cliques duplos não geram envios duplicados, e uma queda momentânea da API não desloga ninguém.

## Estrutura

```
backend/src/main/java/dev/micaelaandrade/pedidos/
├── auth/      cadastro, login, emissão do token, usuário logado
├── order/     entidade Order, regras de status, service, controller, DTOs
├── config/    segurança (JWT, CORS), documentação OpenAPI
└── shared/    tratamento de erros, conversores de dinheiro e data
frontend/src/
├── api/         chamadas HTTP e tipos
├── auth/        sessão do usuário (token)
├── components/  telas e componentes
└── lib/         rótulos de status, formatação, máscaras e busca de CEP
```

## Melhorias além do escopo do desafio

Tudo o que o desafio pede está implementado. Abaixo estão as evoluções que eu faria num produto real de logística de delivery.

**Para a operação**
- **Vários restaurantes na mesma plataforma**: hoje toda a equipe vê todos os pedidos, como num painel de uma única operação. Com vários restaurantes, cada usuário passaria a pertencer a um restaurante, e a API filtraria os pedidos por ele, para que um restaurante nunca veja os pedidos de outro.
- **Atribuir entregador ao pedido** ao sair para entrega, para saber quem está levando cada pedido.
- **Tempo de cada etapa e alerta de atraso**: o histórico de status já guarda os horários, então dá para medir quanto o pedido ficou em preparo ou em rota e destacar os que passaram do tempo esperado.
- **Receber pedidos de outros canais** (marketplaces, site próprio) por integração, sem duplicar pedido quando a mesma notificação chegar duas vezes.
- **Endereço em coordenadas** a partir do CEP, para estimar distância e tempo de entrega e agrupar entregas próximas.

**Técnicas**
- Atualização em tempo real (Server-Sent Events) no lugar da consulta a cada 15 s.
- Paginação e filtro por data na listagem.
- Perfis de acesso (atendente, cozinha, entregador), cada um com as ações que lhe cabem.
- Token em cookie `httpOnly` com refresh token, em vez de `localStorage`.
- PostgreSQL com Testcontainers nos testes e Docker Compose para subir tudo com um comando.

## Uso de IA

Usei o Claude (Anthropic) como apoio no desenvolvimento, como o desafio permite.
