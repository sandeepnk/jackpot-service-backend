CREATE TABLE jackpots (
    jackpot_id VARCHAR(64) PRIMARY KEY,
    initial_pool DECIMAL(24,2) NOT NULL CHECK (initial_pool >= 0),
    current_pool DECIMAL(24,2) NOT NULL CHECK (current_pool >= 0)
);
CREATE TABLE bets (
    bet_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    jackpot_id VARCHAR(64) NOT NULL REFERENCES jackpots(jackpot_id),
    stake_amount DECIMAL(24,2) NOT NULL CHECK (stake_amount > 0),
    won BOOLEAN NOT NULL,
    reward_chance_percent DECIMAL(20,12) NOT NULL CHECK (reward_chance_percent BETWEEN 0 AND 100),
    pool_after_evaluation DECIMAL(24,2) NOT NULL CHECK (pool_after_evaluation >= 0),
    evaluated_at TIMESTAMP(9) WITH TIME ZONE NOT NULL
);
CREATE TABLE jackpot_contributions (
    bet_id VARCHAR(64) PRIMARY KEY REFERENCES bets(bet_id),
    user_id VARCHAR(64) NOT NULL,
    jackpot_id VARCHAR(64) NOT NULL REFERENCES jackpots(jackpot_id),
    stake_amount DECIMAL(24,2) NOT NULL CHECK (stake_amount > 0),
    contribution_amount DECIMAL(24,2) NOT NULL CHECK (contribution_amount >= 0),
    current_jackpot_amount DECIMAL(24,2) NOT NULL CHECK (current_jackpot_amount >= 0),
    created_at TIMESTAMP(9) WITH TIME ZONE NOT NULL
);
CREATE TABLE jackpot_rewards (
    bet_id VARCHAR(64) PRIMARY KEY REFERENCES bets(bet_id),
    user_id VARCHAR(64) NOT NULL,
    jackpot_id VARCHAR(64) NOT NULL REFERENCES jackpots(jackpot_id),
    jackpot_reward_amount DECIMAL(24,2) NOT NULL CHECK (jackpot_reward_amount >= 0),
    created_at TIMESTAMP(9) WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_contributions_jackpot_created ON jackpot_contributions(jackpot_id, created_at);
CREATE INDEX idx_rewards_jackpot_created ON jackpot_rewards(jackpot_id, created_at);
