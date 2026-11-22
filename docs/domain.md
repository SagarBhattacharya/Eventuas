## Components

### User

1. **id**: uuid _generated_
2. **name**: string _required_
3. **email**: string _required_
4. **createdAt**: timestamp _generated_
5. **roles**: {enum} _required_ - [Organizer, Staff, Attendee]

### Events

1. **id**: uuid _generated_
2. **name**: string _required_
3. **startDateTime**: datetime _required_
4. **endDateTime**: datetime _required_
5. **venue**: string _required_
6. **description**: string
7. **salesStartDate**: datetime
8. **salesEndDate**: datetime
9. **createdAt**: timestamp _generated_
10. **status**: enum _required_ - [Draft, Published, Ongoing, Canceled, Completed]

### TicketTier

1. **id**: uuid _generated_
2. **tier**: string _required_
3. **totalStock**: int
4. **price**: bigint _required_
5. **currency**: string _required_
6. **createdAt**: timestamp _generated_

### Ticket

1. **id**: uuid _generated_
2. **purchasedAt**: timestamp _generated_
3. **paid**: bigint _required_
4. **currency**: string _required_
5. **status**: enum _required_ - [Active, Used, Canceled]

### QR code

1. **id**: uuid _generated_
2. **generatedAt**: timestamp _generated_
3. **code**: binary _required_

### TicketValidation

1. **id**: uuid _generated_
2. **validationMethod**: enum _required_ - [Scanned, Manual]
3. **validatedAt**: timestamp _generated_
4. **status**: enum _required_ - [Pending, Failed, Success]

## Relations

- User - has roles - Organizer / Staff / Attendee
- Organizer [1] - creates - Event [0..*]
- Staff [1..*] - manages - Event [0..*]
- Attendee [0..*] - attends - Event [0..*]
- Attendee [1] - purchases - Ticket [0..*]
- Ticket [1] - has a - QR code [1]
- Event [1] - offers - TicketTier [1..*]
- TicketTier [1] - has a - Ticket [0..*]
- Ticket [1] - has - TicketValidation [0..*]
- Staff [1] - performs - TicketValidation [0..*]

## API

### Organizer Endpoints

| Name                 | Endpoint                                       | Request Body            | Response                  |
|----------------------|------------------------------------------------|-------------------------|---------------------------|
| Create Event         | POST /events                                   | EventCreateRequest      | EventResponse             |
| Get All Events       | GET /events                                    | -                       | List<EventResponse>       |
| Get An Event         | GET /events/{eventId}                          | -                       | EventResponse             |
| Update an Event      | PATCH /events/{eventId}                        | EventUpdateRequest      | EventResponse             |
| Delete an Event      | DELETE /events/{eventId}                       | -                       | EventResponse             |
| Create Ticket Tier   | POST /events/{eventId}/ticket-tiers            | TicketTierCreateRequest | TicketTierResponse        |
| Get Ticket Tiers     | GET /events/{eventId}/ticket-tiers             | -                       | List<TicketTierResponse>  |
| Get A Ticket Tier    | GET /events/{eventId}/ticket-tiers/{tierId}    | -                       | TicketTierResponse        |
| Update a Ticket Tier | PATCH /events/{eventId}/ticket-tiers/{tierId}  | TicketTierUpdateRequest | TicketTierResponse        |
| Delete a Ticket Tier | DELETE /events/{eventId}/ticket-tiers/{tierId} | -                       | TicketTierResponse        |
| Get Ticket Sales     | GET /events/{eventId}/tickets                  | -                       | List<TicketResponse>      |
| Get A Ticket Sale    | GET /events/{eventId}/tickets/{ticketId}       | -                       | TicketResponse            |
| Assign Event Staff   | POST /events/{eventId}/staff                   | EventStaffAssignRequest | List<UserSummaryResponse> |
| Get Event Staff      | GET /events/{eventId}/staff                    | -                       | List<UserSummaryResponse> |
| Remove Event Staff   | DELETE /events/{eventId}/staff/{staffId}       | -                       | UserSummaryResponse       |

### Attendee Endpoints

| Name                        | Endpoint                                     | Request Body          | Response                    |
|-----------------------------|----------------------------------------------|-----------------------|-----------------------------|
| Browse Published Events     | GET /events/published                        | -                     | List<EventSummaryResponse>  |
| Get Public Event Details    | GET /events/published/{eventId}              | -                     | EventPublicResponse         |
| View Available Ticket Tiers | GET /events/published/{eventId}/ticket-tiers | -                     | List<TicketTierResponse>    |
| Purchase Ticket(s)          | POST /events/{eventId}/tickets               | TicketPurchaseRequest | List<TicketResponse>        |
| Get My Tickets              | GET /users/me/tickets                        | -                     | List<TicketSummaryResponse> |
| Get Ticket Details          | GET /users/me/tickets/{ticketId}             | -                     | TicketDetailResponse        |
| Get Ticket QR Code          | GET /users/me/tickets/{ticketId}/qr-code     | -                     | QrCodeResponse              |
| Cancel My Ticket            | PATCH /users/me/tickets/{ticketId}/cancel    | -                     | TicketResponse              |

### Staff Endpoints

| Name                     | Endpoint                                  | Request Body                  | Response                       |
|--------------------------|-------------------------------------------|-------------------------------|--------------------------------|
| Get Assigned Events      | GET /staff/events                         | -                             | List<EventResponse>            |
| Validate Ticket (Scan)   | POST /events/{eventId}/validations/scan   | TicketScanValidationRequest   | TicketValidationResponse       |
| Validate Ticket (Manual) | POST /events/{eventId}/validations/manual | TicketManualValidationRequest | TicketValidationResponse       |
| Search Event Tickets     | GET /events/{eventId}/tickets/search      | -                             | List<TicketLookupResponse>     |
| Get Validation History   | GET /events/{eventId}/validations         | -                             | List<TicketValidationResponse> |
| Get Check-In Stats       | GET /events/{eventId}/validations/summary | -                             | EventValidationSummaryResponse |