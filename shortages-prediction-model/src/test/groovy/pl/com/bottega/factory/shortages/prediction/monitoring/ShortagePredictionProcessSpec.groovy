package pl.com.bottega.factory.shortages.prediction.monitoring

import pl.com.bottega.factory.product.management.RefNoId
import pl.com.bottega.factory.shortages.prediction.ConfigurationParams
import pl.com.bottega.factory.shortages.prediction.Shortage
import pl.com.bottega.factory.shortages.prediction.calculation.ShortageForecasts
import pl.com.bottega.factory.shortages.prediction.calculation.ShortagesCalculationAssembler
import spock.lang.Specification

import java.time.LocalDateTime

import static pl.com.bottega.factory.shortages.prediction.monitoring.NewShortage.After

class ShortagePredictionProcessSpec extends Specification {

    def refNo = new RefNoId("3009000")
    def now = LocalDateTime.now()
    def forecastAssembler = new ShortagesCalculationAssembler(refNo: refNo, now: now)
    def events = Mock(ShortageEvents)

    def "Emits no events when there is still no shortages"() {
        given:
        def process = predictionProcess(
                noShortagesWasPreviouslyFound(),
                noShortagesWillBeFound()
        )

        when:
        process.onDemandChanged()

        then:
        0 * events.emit(_ as NewShortage)
        0 * events.emit(_ as ShortageSolved)
    }

    def "Emits NewShortage found when shortage was found first time"() {
        given:
        def process = predictionProcess(
                noShortagesWasPreviouslyFound(),
                willFindShortages(someShortages())
        )

        when:
        process.onDemandChanged()

        then:
        1 * events.emit(newShortage(After.DemandChanged, someShortages()))
        0 * events.emit(_ as ShortageSolved)
    }

    def "Emits ShortageSolved when shortage disappear"() {
        given:
        def process = predictionProcess(
                wasPreviouslyFound(someShortages()),
                noShortagesWillBeFound()
        )

        when:
        process.onDemandChanged()

        then:
        0 * events.emit(_ as NewShortage)
        1 * events.emit(shortageSolved())
    }

    def "Emits no events when there is still 'same' shortages"() {
        given:
        def process = predictionProcess(
                wasPreviouslyFound(someShortages()),
                willFindShortages(someShortages())
        )

        when:
        process.onDemandChanged()

        then:
        0 * events.emit(_ as NewShortage)
        0 * events.emit(_ as ShortageSolved)
    }

    def "Emits NewShortage found when 'different' shortages will be found"() {
        given:
        def process = predictionProcess(
                wasPreviouslyFound(someShortages()),
                willFindShortages(someDifferentShortages())
        )

        when:
        process.onDemandChanged()

        then:
        1 * events.emit(newShortage(After.DemandChanged, someDifferentShortages()))
        0 * events.emit(_ as ShortageSolved)
    }

    def "Remembers last found shortage"() {
        given:
        def process = predictionProcess(
                noShortagesWasPreviouslyFound(),
                willFindShortages(someShortages())
        )

        when:
        process.onDemandChanged()

        then:
        1 * events.emit(newShortage(After.DemandChanged, someShortages()))
        0 * events.emit(_ as ShortageSolved)

        when:
        process.onDemandChanged()
        process.onLockedParts()
        process.onPlanChanged()
        process.onStockChanged()

        then:
        0 * events.emit(_ as NewShortage)
        0 * events.emit(_ as ShortageSolved)
    }

    ShortagePredictionProcess predictionProcess(
            Shortage previouslyFound,
            ShortageForecasts forecastThatWillFindShortages) {

        new ShortagePredictionProcess(
                refNo,
                previouslyFound,
                ShortageDiffPolicy.ValuesAreNotSame,
                forecastThatWillFindShortages,
                defaultConfig(),
                events
        )
    }

    Map<LocalDateTime, Long> someShortages() {
        [(now.plusHours(5)): 500L,
         (now.plusDays(1)) : 500L]
    }

    Map<LocalDateTime, Long> someDifferentShortages() {
        [(now.plusHours(5)): 100L,
         (now.plusDays(1)) : 900L]
    }

    Shortage noShortagesWasPreviouslyFound() {
        null
    }

    Shortage wasPreviouslyFound(Map<LocalDateTime, Long> shortages) {
        forecastAssembler.shortage(shortages).orElse(null)
    }

    ShortageForecasts noShortagesWillBeFound() {
        forecastAssembler.forecastProvider(
                forecastAssembler.stock(1000),
                forecastAssembler.noDeliveries(),
                forecastAssembler.noProductions()
        )
    }

    ShortageForecasts willFindShortages(Map<LocalDateTime, Long> shortages) {
        forecastAssembler.forecastProvider(
                forecastAssembler.stock(0),
                forecastAssembler.deliveries(shortages),
                forecastAssembler.noProductions()
        )
    }

    ConfigurationParams defaultConfig() {
        new InMemoryConfigurationParams(daysAhead: 14)
    }

    NewShortage newShortage(After after, Map<LocalDateTime, Long> missing) {
        new NewShortage(refNo, after, forecastAssembler.shortage(missing).get())
    }

    ShortageSolved shortageSolved() {
        new ShortageSolved(refNo)
    }
}
