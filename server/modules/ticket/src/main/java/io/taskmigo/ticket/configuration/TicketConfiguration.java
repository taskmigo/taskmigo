package io.taskmigo.ticket.configuration;

import io.taskmigo.authorization.object.application.api.ObjectAuthorization;
import io.taskmigo.eav.query.EavQueryFieldCatalog;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.value.application.EavInstanceService;
import io.taskmigo.query.ListQueryCompiler;
import io.taskmigo.ticket.application.TicketQueryFieldCatalog;
import io.taskmigo.ticket.application.TicketQueryRepository;
import io.taskmigo.ticket.application.TicketQuerySchemas;
import io.taskmigo.ticket.application.TicketRepository;
import io.taskmigo.ticket.application.TicketServiceImpl;
import io.taskmigo.ticket.application.api.TicketQueryFieldContexts;
import io.taskmigo.ticket.application.api.TicketService;
import io.taskmigo.ticket.type.application.TicketTypeRepository;
import io.taskmigo.ticket.type.application.TicketTypeServiceImpl;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TicketConfiguration {

    @Bean
    TicketQueryFieldCatalog ticketQueryFieldCatalog(
        EavSchemaService schemas,
        EavInstanceService instances,
        TicketTypeRepository ticketTypes,
        TicketRepository tickets
    ) {
        return new TicketQueryFieldCatalog(new EavQueryFieldCatalog(), schemas, instances, ticketTypes, tickets);
    }

    @Bean
    TicketTypeService ticketTypeService(TicketTypeRepository repository, EavSchemaService schemas) {
        return new TicketTypeServiceImpl(repository, schemas, Clock.systemUTC());
    }

    @Bean
    TicketService ticketService(
        TicketTypeRepository ticketTypes,
        EavSchemaService schemas,
        EavInstanceService instances,
        TicketRepository tickets,
        TicketQueryRepository queries,
        TicketQueryFieldContexts queryFields,
        TicketQuerySchemas querySchemas,
        ObjectAuthorization objectAuthorization
    ) {
        return new TicketServiceImpl(
            ticketTypes,
            schemas,
            instances,
            tickets,
            queries,
            queryFields,
            querySchemas,
            objectAuthorization,
            new ListQueryCompiler(),
            Clock.systemUTC()
        );
    }
}
