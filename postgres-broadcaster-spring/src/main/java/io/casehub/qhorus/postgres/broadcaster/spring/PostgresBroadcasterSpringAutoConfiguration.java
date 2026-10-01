package io.casehub.qhorus.postgres.broadcaster.spring;

import io.casehub.qhorus.api.gateway.ChannelActivityBroadcaster;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.qhorus.runtime.gateway.DeliverySignalQueue;
import org.postgresql.PGConnection;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnClass(PGConnection.class)
public class PostgresBroadcasterSpringAutoConfiguration {

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean(ChannelActivityBroadcaster.class)
    SpringPostgresChannelActivityBroadcaster postgresChannelActivityBroadcaster(
            DataSource dataSource,
            ChannelGateway channelGateway,
            DeliverySignalQueue deliverySignalQueue) {
        return new SpringPostgresChannelActivityBroadcaster(dataSource, channelGateway, deliverySignalQueue);
    }
}
