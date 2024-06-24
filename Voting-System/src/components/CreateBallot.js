import './CreateBallot.css'
import React, { useState } from 'react';
import { ethers } from 'ethers';

function CreateBallot({ pollContract, onPollCreated }) {
    const [title, setTitle] = useState('');
    const [endTime, setEndTime] = useState('');
    const [reward, setReward] = useState('');
    const [joinPrice, setJoinPrice] = useState('');
    const [options, setOptions] = useState('');

    async function createBallot() {
        if (!pollContract) {
            alert('Contract not loaded, please check MetaMask!');
            return;
        }

        try {
            const endTimeStamp = Math.floor(new Date(endTime).getTime() / 1000);
            const rewardInWei = ethers.utils.parseEther(reward);
            const joinPriceInWei = ethers.utils.parseEther(joinPrice);
            const optionsArray = options.split(',').map(option => option.trim());

            const transaction = await pollContract.createPoll(
                title,
                endTimeStamp,
                rewardInWei,
                joinPriceInWei,
                optionsArray,
                { value: rewardInWei }  // 保证发送的ETH足够
            );
            await transaction.wait();

            const pollId = await pollContract.pollCount();
            onPollCreated(pollId.toString());
            alert('Poll created successfully!');
        } catch (error) {
            console.error('Error creating poll:', error);
            alert('Error creating poll: ' + error.message);
        }
    }

    return (
        <div className="create-ballot-container">
            <h2 className="create-ballot-header">Create Poll</h2>
            <p className="create-ballot-description">Type in the settings below.</p>
            <div className="input-row">
                <input value={title} onChange={e => setTitle(e.target.value)} placeholder="Poll Title" />
                <input type="datetime-local" value={endTime} onChange={e => setEndTime(e.target.value)} placeholder="End Time" />
            </div>
            <div className="input-row">
                <input value={reward} onChange={e => setReward(e.target.value)} placeholder="Reward (ETH)" />
                <input value={joinPrice} onChange={e => setJoinPrice(e.target.value)} placeholder="Join Price (ETH)" />
            </div>
            <div className="input-group">
                <input
                    className="full-width options-input" // 确保引入新的样式类
                    value={options}
                    onChange={e => setOptions(e.target.value)}
                    placeholder="Options (comma-separated)"
                />
            </div>
            <button className="create-button" onClick={createBallot}>Create Ballot</button>
        </div>
    );
}

export default CreateBallot;
