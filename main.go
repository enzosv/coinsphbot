package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"math"
	"net/http"
	"os"
	"sort"
	"strconv"
	"strings"

	msg "golang.org/x/text/message"
)

type CoinsResponse struct {
	Markets []Market `json:"markets"`
}

type Market struct {
	Symbol string `json:"symbol"`
	// Currency string `json:"currency"`
	Product string `json:"product"`
	// Bid     string `json:"bid"`
	Ask string `json:"ask"`
	// ExpiresInSeconds int    `json:"expires_in_seconds"`

	Amt float64 `json:"amt"`
}

type Config struct {
	Symbols []string `json:"symbols"` //list of symbols to parse
	BotID   string   `json:"bot_id"`  //telegram bot key
	JbinSK  string   `json:"jbin_sk"` //json bin secret key
	JbinID  string   `json:"jbin_id"` //json bin for old
	BTCID   string   `json:"btc_id"`  //telegam chat id for btc
	AllID   string   `json:"all_id"`  //telegram chat id for all
}

const TGURL = "https://api.telegram.org"
const JBINURL = "https://api.jsonbin.io/b"
const COINSURL = "https://quote.coins.ph/v2/markets?region=PH"

func main() {
	configPath := flag.String("c", "config.json", "config file")
	flag.Parse()
	config := parseConfig(*configPath)
	//fetch prices
	response, err := fetchMarkets(COINSURL)
	if err != nil {
		log.Fatalf("[ERROR] %v", err)
		return
	}
	markets := response.filterMarkets(config.Symbols)
	old, err := fetchOld(JBINURL, config.JbinID, config.JbinSK)
	if err != nil {
		log.Fatalf("[ERROR] %v", err)
		return
	}

	//send to all
	message := constructMessage(markets, *old)

	err = sendMessage(TGURL, config.BotID, config.AllID, message)
	if err != nil {
		log.Fatalf("[ERROR] %v", err)
		return
	}

	// save
	err = updateOld(JBINURL, config.JbinID, config.JbinSK, markets)
	if err != nil {
		log.Fatalf("[ERROR] %v", err)
		return
	}

	//send to btc
	btc := constructCurrencyMessage(markets, *old, "BTC-PHP")
	sendMessage(TGURL, config.BotID, config.BTCID, btc)
}

func parseConfig(path string) Config {
	configFile, err := os.Open(path)
	if err != nil {
		log.Fatal("Cannot open server configuration file: ", err)
	}
	defer configFile.Close()

	dec := json.NewDecoder(configFile)
	var config Config
	if err = dec.Decode(&config); errors.Is(err, io.EOF) {
		//do nothing
	} else if err != nil {
		log.Fatal("Cannot load server configuration file: ", err)
	}
	return config
}

func fetchMarkets(url string) (CoinsResponse, error) {
	res, err := http.Get(url)
	if err != nil {
		return CoinsResponse{}, err
	}
	defer res.Body.Close()
	body, err := ioutil.ReadAll(res.Body)
	if err != nil {
		return CoinsResponse{}, err
	}
	var markets CoinsResponse
	err = json.Unmarshal(body, &markets)
	if err != nil {
		return CoinsResponse{}, err
	}
	return markets, nil
}

func (c *CoinsResponse) filterMarkets(symbols []string) []Market {
	filtered := []Market{}
	for _, m := range c.Markets {
		if !checkContains(symbols, m.Symbol) {
			continue
		}
		amt, err := strconv.ParseFloat(m.Ask, 64)
		if err != nil {
			log.Fatal(err)
		}
		m.Amt = amt
		filtered = append(filtered, m)
	}
	sort.SliceStable(filtered, func(i, j int) bool {
		return filtered[i].Amt > filtered[j].Amt
	})
	return filtered
}

func constructSingleMessage(new Market, old Market, printer *msg.Printer) string {
	dif := new.Amt - old.Amt
	amtStr := printer.Sprintf("₱%d", int(new.Amt))
	difStr := printer.Sprintf("₱%d", int(math.Abs(dif)))
	if new.Amt < 1000 {
		amtStr = printer.Sprintf("₱%.2f", new.Amt)
		difStr = printer.Sprintf("₱%.2f", math.Abs(dif))
	}
	if dif == 0 {
		return amtStr
	}
	if dif > 0 {
		return printer.Sprintf("*%s (+%s)* ", amtStr, difStr)
	}
	return printer.Sprintf("`%s (-%s)`", amtStr, difStr)
}

func constructCurrencyMessage(markets []Market, old []Market, symbol string) string {
	for _, m := range markets {
		if m.Symbol != symbol {
			continue
		}
		for _, o := range old {
			if o.Symbol != symbol {
				continue
			}
			return constructSingleMessage(m, o, msg.NewPrinter(msg.MatchLanguage("en")))
		}
	}
	return ""
}

func constructMessage(markets []Market, old []Market) string {
	message := []string{}
	p := msg.NewPrinter(msg.MatchLanguage("en"))
	for _, m := range markets {
		for _, o := range old {
			if o.Symbol != m.Symbol {
				continue
			}
			message = append(message, fmt.Sprintf("%s: %s", m.Product, constructSingleMessage(m, o, p)))
			break
		}
	}
	return strings.Join(message, "\n")
}

func constructPayload(chatID string, message string) (*bytes.Reader, error) {
	payload := map[string]interface{}{}
	payload["chat_id"] = chatID
	payload["text"] = message
	payload["parse_mode"] = "markdown"

	jsonValue, err := json.Marshal(payload)
	return bytes.NewReader(jsonValue), err
}

func checkContains(slice []string, value string) bool {
	for i := 0; i < len(slice); i++ {
		if slice[i] == value {
			return true
		}
	}
	return false
}

func sendMessage(url string, bot string, chatID string, message string) error {
	payload, err := constructPayload(chatID, message)
	if err != nil {
		return err
	}
	req, err := http.NewRequest("POST", fmt.Sprintf("%s/bot%s/sendMessage", url, bot), payload)
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	_, err = ioutil.ReadAll(res.Body)
	if err != nil {
		return err
	}
	return nil
}

func fetchOld(url string, id string, key string) (*[]Market, error) {
	req, err := http.NewRequest("GET", fmt.Sprintf("%s/%s/latest", url, id), nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("secret-key", key)
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()
	body, err := ioutil.ReadAll(res.Body)
	if err != nil {
		return nil, err
	}
	var markets CoinsResponse
	err = json.Unmarshal(body, &markets)
	if err != nil {
		return nil, err
	}
	return &markets.Markets, nil
}

func updateOld(url string, id string, key string, markets []Market) error {
	b, err := json.Marshal(CoinsResponse{markets})
	if err != nil {
		return err
	}
	req, err := http.NewRequest("PUT", fmt.Sprintf("%s/%s", url, id), bytes.NewBuffer(b))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("secret-key", key)
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	_, err = ioutil.ReadAll(res.Body)
	if err != nil {
		return err
	}
	return nil
}
